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

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class PaymentWorkflowService {
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
            if (!recovery
                    && !work.compensating()
                    && exception.failureType() == GatewayFailureType.REJECTED) {
                try {
                    return transactionService.rejected(
                            userId, work.operationId(), exception.code());
                } catch (RuntimeException saveFailure) {
                    return markUnknown(userId, work, "FAILURE_SAVE_PENDING");
                }
            }
            return markUnknown(userId, work, exception.code());
        } catch (RuntimeException exception) {
            return markUnknown(userId, work, "GATEWAY_OUTCOME_UNKNOWN");
        }
        try {
            if (recovery
                    && work.compensating()
                    && transactionService.compensationCanResume(userId, work.operationId(), result))
                return execute(
                        userId, transactionService.recovery(userId, work.operationId()), false);
            if (work.compensating() || work.type() == PaymentOperationType.CANCEL)
                return transactionService.canceled(userId, work.operationId(), result);
            return recovery
                    ? transactionService.recoverApproval(userId, work.operationId(), result)
                    : transactionService.approved(userId, work.operationId(), result);
        } catch (BusinessException exception) {
            if (work.type() == PaymentOperationType.CONFIRM
                    && !work.compensating()
                    && permanentConfirmationFailure(exception.getErrorCode())) {
                try {
                    return execute(
                            userId,
                            transactionService.startCompensation(
                                    userId, work.operationId(), exception.getErrorCode().name()),
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

    private boolean permanentConfirmationFailure(ErrorCode code) {
        return code == ErrorCode.SEAT_OWNER_MISMATCH
                || code == ErrorCode.RESERVATION_NOT_FOUND
                || code == ErrorCode.HOLD_NOT_ACTIVE
                || code == ErrorCode.SCREENING_SEATS_NOT_READY;
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
