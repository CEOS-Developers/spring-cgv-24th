package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentOrderCreateRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationType;
import com.ceos.cgv.domain.payment.gateway.CancelPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.ConfirmPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.PaymentGateway;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.service.result.PaymentOrderCreationResult;
import com.ceos.cgv.domain.payment.service.result.PaymentWork;
import com.ceos.cgv.domain.payment.service.transaction.PaymentTransactionService;
import com.ceos.cgv.domain.reservation.service.ReservationService;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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
    private final PaymentQueryService queryService;
    private final ReservationService reservationService;

    public PaymentOrderResponse createOrder(Long userId, PaymentOrderCreateRequest request) {
        return transactionService.createOrder(userId, request).response();
    }

    public PaymentOrderCreationResult createOrderResult(
            Long userId, PaymentOrderCreateRequest request) {
        return transactionService.createOrder(userId, request);
    }

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

    public PaymentOperationResponse cancelReservation(Long userId, Long reservationId, UUID key) {
        Optional<Long> paymentId = queryService.paymentIdForReservation(userId, reservationId);
        if (paymentId.isEmpty()) {
            reservationService.cancel(reservationId, userId);
            return null;
        }
        if (key == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return cancel(userId, paymentId.get(), key, new PaymentCancelRequest(null, "예매 전체 취소"));
    }

    private PaymentOperationResponse execute(Long userId, PaymentWork work, boolean recovery) {
        if (!work.callGateway()) return work.response();
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
                return approvalNotFound(userId, work, exception.code());
            if (!recovery
                    && !work.compensating()
                    && exception.failureType() == GatewayFailureType.REJECTED) {
                try {
                    return finalizeWithRetry(
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
            if (recovery && canResend(userId, work, result))
                return execute(
                        userId, transactionService.recovery(userId, work.operationId()), false);
            if (work.compensating() || work.type() == PaymentOperationType.CANCEL)
                return finalizeWithRetry(
                        () -> transactionService.canceled(userId, work.operationId(), result));
            return recovery
                    ? finalizeWithRetry(
                            () ->
                                    transactionService.recoverApproval(
                                            userId, work.operationId(), result))
                    : finalizeWithRetry(
                            () -> transactionService.approved(userId, work.operationId(), result));
        } catch (BusinessException exception) {
            if (work.type() == PaymentOperationType.CONFIRM
                    && !work.compensating()
                    && permanentConfirmationFailure(exception.getErrorCode())) {
                try {
                    return execute(
                            userId,
                            finalizeWithRetry(
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

    private boolean canResend(Long userId, PaymentWork work, GatewayPayment result) {
        if (work.compensating())
            return finalizeWithRetry(
                    () ->
                            transactionService.compensationCanResume(
                                    userId, work.operationId(), result));
        if (work.type() == PaymentOperationType.CANCEL)
            return finalizeWithRetry(
                    () ->
                            transactionService.cancellationCanResume(
                                    userId, work.operationId(), result));
        return false;
    }

    private PaymentOperationResponse approvalNotFound(
            Long userId, PaymentWork work, String code) {
        try {
            return finalizeWithRetry(
                    () -> transactionService.approvalNotFound(userId, work.operationId(), code));
        } catch (RuntimeException saveFailure) {
            return markUnknown(userId, work, code);
        }
    }

    private boolean permanentConfirmationFailure(ErrorCode code) {
        return code == ErrorCode.SEAT_OWNER_MISMATCH
                || code == ErrorCode.RESERVATION_NOT_FOUND
                || code == ErrorCode.HOLD_NOT_ACTIVE
                || code == ErrorCode.SCREENING_SEATS_NOT_READY;
    }

    // PG 결과를 받은 뒤의 확정은 짧은 좌석 NOWAIT 충돌에만 새 트랜잭션으로 몇 번 더 시도한다.
    private <T> T finalizeWithRetry(Supplier<T> finalization) {
        for (int attempt = 1; ; attempt++) {
            try {
                return finalization.get();
            } catch (BusinessException exception) {
                if (exception.getErrorCode() != ErrorCode.SEAT_BUSY
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
