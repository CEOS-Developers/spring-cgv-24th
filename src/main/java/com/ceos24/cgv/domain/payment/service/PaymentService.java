package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.TossPaymentException;
import com.ceos24.cgv.domain.payment.client.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentCommandResponse;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.PaymentOperationType;
import com.ceos24.cgv.domain.payment.service.PaymentTransactionService.Preparation;
import com.ceos24.cgv.domain.payment.service.PaymentTransactionService.Work;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PaymentService {

    private static final Duration PG_REPLAY_WINDOW = Duration.ofDays(15).minusMinutes(1);
    private static final Set<String> CONFIRM_REJECTIONS = Set.of(
            "REJECT_CARD_PAYMENT", "REJECT_ACCOUNT_PAYMENT", "REJECT_CARD_COMPANY", "INVALID_REJECT_CARD",
            "BELOW_MINIMUM_AMOUNT", "INVALID_CARD_EXPIRATION", "INVALID_STOPPED_CARD");
    private static final Set<String> CANCEL_REJECTIONS = Set.of(
            "NOT_CANCELABLE_AMOUNT", "NOT_CANCELABLE_PAYMENT", "NOT_ALLOWED_PARTIAL_REFUND", "EXCEED_MAX_REFUND_DUE");

    private final PaymentTransactionService transactions;
    private final TossPaymentClient toss;

    public PaymentCommandResponse confirm(String username, PaymentConfirmRequest request, String idempotencyKey) {
        return execute(transactions.prepareConfirm(username, request, idempotencyKey));
    }

    public PaymentCommandResponse cancel(String username, Long paymentId, PaymentCancelRequest request, String idempotencyKey) {
        return execute(transactions.prepareCancel(username, paymentId, request, idempotencyKey));
    }

    public PaymentResponse getPayment(String username, Long paymentId) {
        return transactions.getPayment(username, paymentId);
    }

    private PaymentCommandResponse execute(Preparation preparation) {
        if (preparation.rejection() != null) {
            throw new BusinessException(preparation.rejection());
        }
        if (preparation.replay() != null) {
            return preparation.replay();
        }
        Work work = preparation.work();
        try {
            if (work.recovery() && work.type() == PaymentOperationType.CONFIRM) {
                TossPaymentResponse actual = toss.getPayment(work.paymentKey());
                validateRecoveryIdentity(work, actual);
                if ("DONE".equals(actual.status())) {
                    return finish(work, actual);
                }
                if ("ABORTED".equals(actual.status()) || "EXPIRED".equals(actual.status())) {
                    return reject(work, "PG_" + actual.status(), "PG에서 승인되지 않은 종료 상태를 확인했습니다.");
                }
                if (!"IN_PROGRESS".equals(actual.status()) && !"READY".equals(actual.status())) {
                    return uncertain(work, TossPaymentException.invalidResponse(null, null));
                }
            }
            if (!Instant.now().isBefore(work.requestedAt().plus(PG_REPLAY_WINDOW))) {
                PaymentCommandResponse completed = transactions.recordError(work, false,
                        "PG_REPLAY_WINDOW_EXPIRED", "PG 멱등키 유효기간 내 재시도가 아닙니다.");
                if (completed != null) {
                    return completed;
                }
                throw new BusinessException(ErrorCode.PAYMENT_MANUAL_REVIEW_REQUIRED);
            }
            TossPaymentResponse response = work.type() == PaymentOperationType.CONFIRM
                    ? toss.confirm(new TossConfirmRequest(work.paymentKey(), work.orderId(), work.amount()), work.pgIdempotencyKey())
                    : toss.cancel(work.paymentKey(), TossCancelRequest.partial(work.cancelReason(), work.amount()), work.pgIdempotencyKey());
            return finish(work, response);
        } catch (TossPaymentException exception) {
            Set<String> rejections = work.type() == PaymentOperationType.CONFIRM ? CONFIRM_REJECTIONS : CANCEL_REJECTIONS;
            if (exception.getReason() == TossPaymentException.Reason.API_ERROR
                    && (Objects.equals(exception.getHttpStatus(), 403) || Objects.equals(exception.getHttpStatus(), 400))
                    && rejections.contains(exception.getErrorCode())) {
                return reject(work, exception.getErrorCode(), exception.getProviderMessage());
            }
            return uncertain(work, exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return uncertain(work, exception);
        }
    }

    private PaymentCommandResponse finish(Work work, TossPaymentResponse response) {
        try {
            return transactions.complete(work, response);
        } catch (RuntimeException exception) {
            // DB 롤백으로 PG 성공을 취소할 수 없으므로 작업을 보존하고 같은 키로 복구한다.
            return uncertain(work, exception);
        }
    }

    private PaymentCommandResponse reject(Work work, String code, String message) {
        try {
            PaymentCommandResponse completed = transactions.recordError(work, true, code, message);
            if (completed != null) {
                return completed;
            }
        } catch (RuntimeException exception) {
            return uncertain(work, exception);
        }
        throw new BusinessException(ErrorCode.PAYMENT_OPERATION_FAILED);
    }

    private PaymentCommandResponse uncertain(Work work, RuntimeException cause) {
        String code = cause instanceof TossPaymentException exception ? exception.getErrorCode() : "LOCAL_RESULT_SAVE_FAILED";
        try {
            PaymentCommandResponse completed = transactions.recordError(work, false, code, "결과를 확인한 뒤 복구가 필요합니다.");
            if (completed != null) {
                return completed;
            }
        } catch (RuntimeException saveFailure) {
            cause.addSuppressed(saveFailure);
            log.error("결제 작업 결과 기록 실패: operationId={}", work.operationId(), saveFailure);
        }
        BusinessException exception = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        exception.initCause(cause);
        throw exception;
    }

    private void validateRecoveryIdentity(Work work, TossPaymentResponse response) {
        if (response == null || !work.paymentKey().equals(response.paymentKey())
                || !work.orderId().equals(response.orderId()) || response.totalAmount() == null
                || response.totalAmount() != work.amount() || !"KRW".equals(response.currency())) {
            throw TossPaymentException.invalidResponse(null, null);
        }
    }
}
