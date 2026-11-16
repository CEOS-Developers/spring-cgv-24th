package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationType;
import com.ceos.cgv.domain.payment.gateway.CancelPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.ConfirmPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.PaymentGateway;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.service.result.PaymentWork;
import com.ceos.cgv.domain.payment.service.target.PaymentTarget;
import com.ceos.cgv.domain.payment.service.target.PaymentTarget.FinalizationFailure;
import com.ceos.cgv.domain.payment.service.target.PaymentTargetRegistry;
import com.ceos.cgv.domain.payment.service.transaction.PaymentTransactionService;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class PaymentWorkflowService {
    private static final int FINALIZE_MAX_ATTEMPTS = 3;
    private static final long FINALIZE_BACKOFF_MILLIS = 50;

    private final PaymentTransactionService transactionService;
    private final PaymentGateway paymentGateway;
    private final PaymentTargetRegistry paymentTargets;

    public PaymentOperationResponse confirm(Long userId, UUID key, PaymentConfirmRequest request) {
        verifyGatewayReady();
        PaymentWork work;
        try {
            work = transactionService.claimApproval(userId, key, request);
        } catch (DataIntegrityViolationException conflict) {
            // A global paymentKey or request-key contender may have committed first.
            try {
                work = transactionService.claimApproval(userId, key, request);
            } catch (DataIntegrityViolationException repeated) {
                throw new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
            }
        }
        return execute(userId, work, false);
    }

    public PaymentOperationResponse cancel(
            Long userId, Long paymentId, UUID key, PaymentCancelRequest request) {
        verifyGatewayReady();
        PaymentWork work;
        try {
            work = transactionService.claimCancellation(userId, paymentId, key, request);
        } catch (DataIntegrityViolationException conflict) {
            try {
                work = transactionService.claimCancellation(userId, paymentId, key, request);
            } catch (DataIntegrityViolationException repeated) {
                throw new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
            }
        }
        return execute(userId, work, false);
    }

    public PaymentOperationResponse reconcile(Long userId, Long operationId) {
        verifyGatewayReady();
        return execute(userId, transactionService.recovery(userId, operationId), true);
    }

    private PaymentOperationResponse execute(Long userId, PaymentWork work, boolean recovery) {
        if (!work.callGateway()) return work.response();
        PaymentTarget target = paymentTargets.get(work.targetType());
        GatewayPayment result;
        try {
            if (recovery) result = paymentGateway.getPayment(work.paymentKey());
            else if (work.compensating() || work.type() == PaymentOperationType.CANCEL)
                result =
                        paymentGateway.cancel(
                                new CancelPaymentCommand(
                                        work.paymentKey(),
                                        work.orderId(),
                                        work.amount(),
                                        work.previousBalance(),
                                        work.reason(),
                                        work.gatewayKey()));
            else
                result =
                        paymentGateway.confirm(
                                new ConfirmPaymentCommand(
                                        work.paymentKey(),
                                        work.orderId(),
                                        work.amount(),
                                        work.gatewayKey()));
        } catch (PaymentGatewayException exception) {
            if (recovery
                    && !work.compensating()
                    && work.type() == PaymentOperationType.CONFIRM
                    && exception.failureType() == GatewayFailureType.NOT_FOUND)
                return approvalNotFound(userId, work, target, exception.code());
            if (!recovery
                    && !work.compensating()
                    && exception.failureType() == GatewayFailureType.REJECTED) {
                try {
                    return finalizeWithRetry(
                            target,
                            () ->
                                    transactionService.rejected(
                                            userId, work.operationId(), exception.code()));
                } catch (RuntimeException saveFailure) {
                    return markUnknown(userId, work, "FAILURE_SAVE_PENDING");
                }
            }
            return markUnknown(userId, work, exception.code());
        } catch (RuntimeException exception) {
            return markUnknown(userId, work, "GATEWAY_OUTCOME_UNKNOWN");
        }
        try {
            // 복구 조회로 재전송이 안전함이 확인되면 저장된 같은 멱등키로 한 번만 다시 실행한다.
            if (recovery && canResend(userId, work, target, result))
                return execute(
                        userId, transactionService.recovery(userId, work.operationId()), false);
            if (work.compensating() || work.type() == PaymentOperationType.CANCEL)
                return finalizeWithRetry(
                        target,
                        () -> transactionService.canceled(userId, work.operationId(), result));
            return recovery
                    ? finalizeWithRetry(
                            target,
                            () ->
                                    transactionService.recoverApproval(
                                            userId, work.operationId(), result))
                    : finalizeWithRetry(
                            target,
                            () -> transactionService.approved(userId, work.operationId(), result));
        } catch (BusinessException exception) {
            if (work.type() == PaymentOperationType.CONFIRM
                    && !work.compensating()
                    && target.classifyFinalizationFailure(exception.getErrorCode())
                            == FinalizationFailure.COMPENSATE_APPROVAL) {
                try {
                    return execute(
                            userId,
                            finalizeWithRetry(
                                    target,
                                    () ->
                                            transactionService.startCompensation(
                                                    userId,
                                                    work.operationId(),
                                                    exception.getErrorCode().name())),
                            false);
                } catch (RuntimeException compensationFailure) {
                    return markUnknown(userId, work, "COMPENSATION_SAVE_PENDING");
                }
            }
            return markUnknown(userId, work, "FINALIZATION_PENDING");
        } catch (RuntimeException exception) {
            return markUnknown(userId, work, "FINALIZATION_PENDING");
        }
    }

    private boolean canResend(
            Long userId, PaymentWork work, PaymentTarget target, GatewayPayment result) {
        if (work.compensating())
            return finalizeWithRetry(
                    target,
                    () ->
                            transactionService.compensationCanResume(
                                    userId, work.operationId(), result));
        if (work.type() == PaymentOperationType.CANCEL)
            return finalizeWithRetry(
                    target,
                    () ->
                            transactionService.cancellationCanResume(
                                    userId, work.operationId(), result));
        return false;
    }

    private PaymentOperationResponse approvalNotFound(
            Long userId, PaymentWork work, PaymentTarget target, String code) {
        try {
            return finalizeWithRetry(
                    target,
                    () -> transactionService.approvalNotFound(userId, work.operationId(), code));
        } catch (RuntimeException saveFailure) {
            return markUnknown(userId, work, code);
        }
    }

    // PG 응답을 다시 보내지 않고 DB 확정만 새 트랜잭션에서 재시도한다.
    private <T> T finalizeWithRetry(PaymentTarget target, Supplier<T> finalization) {
        for (int attempt = 1; ; attempt++) {
            try {
                return finalization.get();
            } catch (BusinessException exception) {
                if (target.classifyFinalizationFailure(exception.getErrorCode())
                                != FinalizationFailure.RETRY
                        || attempt >= FINALIZE_MAX_ATTEMPTS) throw exception;
                try {
                    Thread.sleep(FINALIZE_BACKOFF_MILLIS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
    }

    private void verifyGatewayReady() {
        try {
            paymentGateway.verifyReady();
        } catch (PaymentGatewayException exception) {
            // PG 키 설정이 없으면 DB 상태를 바꾸기 전에 거절한다.
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private PaymentOperationResponse markUnknown(Long userId, PaymentWork work, String code) {
        try {
            return transactionService.unknown(userId, work.operationId(), code);
        } catch (RuntimeException saveFailure) {
            // Claim was committed before the network call. Even a DB outage keeps a recoverable
            // operation ID.
            return work.response();
        }
    }
}
