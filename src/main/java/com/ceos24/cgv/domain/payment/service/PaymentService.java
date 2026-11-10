package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.domain.payment.service.PaymentTransactionService.CancelPlan;
import com.ceos24.cgv.domain.payment.service.PaymentTransactionService.CompleteResult;
import com.ceos24.cgv.domain.payment.service.PaymentTransactionService.ConfirmStartResult;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Set<String> NON_TERMINAL_CLIENT_ERRORS =
            Set.of("UNAUTHORIZED_KEY", "INVALID_API_KEY", "FORBIDDEN_REQUEST", "TOO_MANY_REQUESTS");

    private final PaymentTransactionService paymentTransactionService;
    private final TossPaymentsClient tossPaymentsClient;

    public PaymentPrepareResponse preparePayment(Long memberId, PaymentPrepareRequest request) {
        validatePrepareRequest(request);
        return paymentTransactionService.preparePayment(memberId, request);
    }

    public PaymentResponse confirmPayment(Long memberId, PaymentConfirmRequest request) {
        // T1: 비관적 락 기반 사전 검증 및 IN_PROGRESS 전이
        ConfirmStartResult startResult = paymentTransactionService.startConfirm(memberId, request);
        if (startResult.alreadyCompleted()) {
            return startResult.response();
        }
        if (startResult.recoveryRequired()) {
            return recoverFromPendingConfirm(request.orderId(), startResult.paymentKey(), startResult.amount());
        }

        // T1과 T2 사이: 외부 토스 승인 API 호출 (orderId를 Idempotency-Key로 사용)
        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentsClient.confirm(
                    new TossConfirmRequest(request.paymentKey(), request.orderId(), request.amount()),
                    request.orderId());
        } catch (TossPaymentException e) {
            log.error(
                    "토스 결제 승인 API 호출 실패: orderId={}, code={}, message={}",
                    request.orderId(),
                    e.getTossCode(),
                    e.getMessage());
            if (e.isClientError()) {
                if ("ALREADY_PROCESSED_PAYMENT".equals(e.getTossCode())) {
                    log.warn(
                            "토스 이미 승인 완료 응답 수신(ALREADY_PROCESSED_PAYMENT) -> 단건 조회 복구 실행: orderId={}",
                            request.orderId());
                    return recoverFromPendingConfirm(request.orderId(), request.paymentKey(), request.amount());
                }
                if (NON_TERMINAL_CLIENT_ERRORS.contains(e.getTossCode())) {
                    // 키 오류/요청 제한 등 재시도 가능한 오류는 결제를 중단 처리하지 않는다. (IN_PROGRESS 유지 → 재시도 시 단건 조회 복구)
                    log.warn(
                            "재시도 가능한 토스 4xx 오류로 결제 중단 처리를 보류합니다: orderId={}, code={}",
                            request.orderId(),
                            e.getTossCode());
                    throw e;
                }
                paymentTransactionService.recordAbortByOrderId(request.orderId());
                throw e;
            }
            log.warn("토스 결제 승인 서버 에러/타임아웃 발생 -> 단건 조회 복구 실행: orderId={}", request.orderId());
            return recoverFromPendingConfirm(request.orderId(), request.paymentKey(), request.amount());
        } catch (Exception e) {
            log.warn("토스 결제 승인 중 예외 발생 -> 단건 조회 복구 실행: orderId={}, error={}", request.orderId(), e.getMessage());
            return recoverFromPendingConfirm(request.orderId(), request.paymentKey(), request.amount());
        }

        // 응답 필수값 및 금액 무결성 검증 (실패 시 즉시 자동 보상 취소)
        try {
            tossResponse.validateIntegrity(request.orderId(), request.amount());
        } catch (BusinessException integrityEx) {
            log.error("토스 승인 응답 검증 실패: orderId={}, error={}", request.orderId(), integrityEx.getMessage());
            executeCompensatingCancel(
                    request.orderId(),
                    tossResponse.paymentKey() != null ? tossResponse.paymentKey() : request.paymentKey(),
                    // 클라이언트 요청 금액이 아닌 토스가 실제 승인한 금액으로 전액 보상 취소
                    tossResponse.totalAmount() != null ? tossResponse.totalAmount() : request.amount(),
                    "비정상 승인 응답 수신으로 인한 자동 보상 취소",
                    integrityEx);
        }

        // T2: 승인 확정 및 자원 반영 (실패 시 즉시 자동 보상 취소)
        return applyApprovedPayment(tossResponse, true);
    }

    private PaymentResponse recoverFromPendingConfirm(String orderId, String paymentKey, Long amount) {
        log.info("결제 승인 복구 조회 시작: orderId={}, paymentKey={}", orderId, paymentKey);
        TossPaymentResponse latest;
        try {
            latest = tossPaymentsClient.getPayment(paymentKey);
        } catch (Exception inquiryEx) {
            log.error(
                    "결제 승인 복구 조회 실패: orderId={}, paymentKey={}, error={}", orderId, paymentKey, inquiryEx.getMessage());
            throw new BusinessException(
                    ErrorCode.PAYMENT_GATEWAY_TIMEOUT, "결제 승인 결과 확인 중 시간 초과가 발생했습니다. 잠시 후 결제 내역을 확인해 주세요.");
        }

        String status = latest.status();
        if ("DONE".equals(status)) {
            latest.validateIntegrity(orderId, amount);
            return applyApprovedPayment(latest, true);
        }
        if ("CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status)) {
            paymentTransactionService.applyCancelWebhook(latest);
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CANCELED, "결제 승인 직후 취소된 거래입니다.");
        }
        if ("ABORTED".equals(status) || "EXPIRED".equals(status)) {
            paymentTransactionService.recordAbortByOrderId(orderId);
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED, "결제 승인이 중단되었거나 만료되었습니다.");
        }

        throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
    }

    private PaymentResponse applyApprovedPayment(
            TossPaymentResponse tossResponse, boolean throwBusinessExceptionAfterCompensation) {
        CompleteResult result;
        try {
            result = paymentTransactionService.completeApprovedPayment(tossResponse);
        } catch (Exception dbException) {
            log.error(
                    "결제 승인 후 DB 반영 중 오류 발생으로 자동 보상 취소 진행: orderId={}, error={}",
                    tossResponse.orderId(),
                    dbException.getMessage(),
                    dbException);

            RuntimeException exceptionToThrow;
            String reason;
            if (dbException instanceof BusinessException be
                    && (be.getErrorCode() == ErrorCode.RESERVATION_EXPIRED
                            || be.getErrorCode() == ErrorCode.RESERVATION_ALREADY_PROCESSED)) {
                reason = "예매 점유 만료 또는 상태 변경으로 인한 자동 보상 취소";
                exceptionToThrow = be;
            } else if (dbException instanceof BusinessException be
                    && be.getErrorCode() == ErrorCode.PAYMENT_AMOUNT_MISMATCH) {
                reason = "결제 금액 불일치로 인한 자동 보상 취소";
                exceptionToThrow = be;
            } else {
                reason = "시스템 처리 오류로 인한 자동 보상 취소";
                exceptionToThrow =
                        new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR, "주문/예매 처리 중 오류가 발생하여 결제가 자동으로 취소되었습니다.");
            }

            executeCompensatingCancel(
                    tossResponse.orderId(),
                    tossResponse.paymentKey(),
                    tossResponse.totalAmount(),
                    reason,
                    throwBusinessExceptionAfterCompensation ? exceptionToThrow : null);

            return null;
        }

        // 보상 취소 대상이 아닌 정상 흐름의 결과 분기 (위 try 밖에서 처리해야 자기 예외를 다시 포획하지 않는다)
        if (result.alreadyAborted() && throwBusinessExceptionAfterCompensation) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 중단되거나 보상 취소된 결제입니다.");
        }
        return result.response();
    }

    /** amount가 null이면 토스에 전액 취소로 요청한다. */
    private void executeCompensatingCancel(
            String orderId, String paymentKey, Long amount, String reason, RuntimeException exceptionToThrowOnSuccess) {
        log.warn("보상 취소 실행: orderId={}, paymentKey={}, amount={}, reason={}", orderId, paymentKey, amount, reason);
        try {
            tossPaymentsClient.cancel(paymentKey, new TossCancelRequest(reason, amount), "COMPENSATION_" + orderId);
            log.info("보상 취소 API 성공: orderId={}", orderId);
        } catch (Exception cancelEx) {
            log.error(
                    "보상 취소 실패 (수동 확인 필요): orderId={}, paymentKey={}, error={}",
                    orderId,
                    paymentKey,
                    cancelEx.getMessage(),
                    cancelEx);
            paymentTransactionService.recordManualRecoveryRequired(orderId, paymentKey);
            throw new BusinessException(
                    ErrorCode.PAYMENT_COMPENSATION_FAILED, "시스템 오류 발생 후 자동 결제 취소에도 실패했습니다. 관리자 확인이 필요합니다.");
        }

        // 토스 취소는 성공했으므로 DB 기록 실패를 '취소 실패(MANUAL_RECOVERY)'로 오표시하지 않는다.
        // 결제는 IN_PROGRESS로 남지만 토스가 CANCELED이므로 웹훅/보정 작업이 수렴시킨다.
        try {
            paymentTransactionService.recordCompensatedAbort(orderId, paymentKey);
        } catch (Exception recordEx) {
            log.error(
                    "보상 취소는 성공했으나 DB 상태 기록 실패 (웹훅/보정 작업으로 수렴 예정): orderId={}, paymentKey={}, error={}",
                    orderId,
                    paymentKey,
                    recordEx.getMessage(),
                    recordEx);
        }

        if (exceptionToThrowOnSuccess != null) {
            throw exceptionToThrowOnSuccess;
        }
    }

    public void completeApprovedPaymentFromWebhook(TossPaymentResponse tossResponse) {
        applyApprovedPayment(tossResponse, false);
    }

    /** 승인 중(IN_PROGRESS) 상태로 오래 남은 결제를 토스 단건 조회 결과로 수렴시킨다. (웹훅 누락 대비) */
    public void reconcileStuckPayment(String orderId) {
        String paymentKey = paymentTransactionService.resolvePaymentKeyByOrderId(orderId);
        if (paymentKey == null || paymentKey.isBlank()) {
            return;
        }
        TossPaymentResponse latest = tossPaymentsClient.getPayment(paymentKey);
        String status = latest.status();
        if ("DONE".equals(status)) {
            completeApprovedPaymentFromWebhook(latest);
        } else if ("CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status)) {
            paymentTransactionService.applyCancelWebhook(latest);
        } else if ("ABORTED".equals(status) || "EXPIRED".equals(status)) {
            paymentTransactionService.abortPendingPaymentFromWebhook(orderId, paymentKey);
        } else {
            log.info("보정 대상 결제가 토스에서도 미확정 상태입니다: orderId={}, status={}", orderId, status);
        }
    }

    public PaymentResponse getPayment(Long memberId, String paymentKey) {
        return paymentTransactionService.getPayment(memberId, paymentKey);
    }

    public PaymentResponse cancelPayment(Long memberId, String paymentKey, PaymentCancelRequest request) {
        // T1: 비관적 락 기반 유효성 검증 및 취소 계획 수립
        CancelPlan plan = paymentTransactionService.prepareCancel(memberId, paymentKey, request);
        if (plan.alreadyCanceled()) {
            return plan.response();
        }

        // T1과 T2 사이: 외부 토스 취소 API 호출 (멱등키 전송)
        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentsClient.cancel(
                    paymentKey,
                    new TossCancelRequest(request.cancelReason(), plan.cancelAmount()),
                    plan.effectiveIdempotencyKey());
        } catch (Exception e) {
            log.error(
                    "토스 결제 취소 API 호출 실패: paymentKey={}, amount={}, error={}",
                    paymentKey,
                    plan.cancelAmount(),
                    e.getMessage());
            throw e;
        }

        // 토스 응답의 최신 취소 이력에서 transactionKey와 canceledAt 추출
        String transactionKey = null;
        LocalDateTime canceledAt = null;
        if (tossResponse.cancels() != null && !tossResponse.cancels().isEmpty()) {
            TossPaymentResponse.TossCancel latestCancel =
                    tossResponse.cancels().get(tossResponse.cancels().size() - 1);
            transactionKey = latestCancel.transactionKey();
            canceledAt = parseTossDateTime(latestCancel.canceledAt());
        }

        // T2: 도메인 잔액 차감, 자원(좌석/재고) 복구 및 취소 이력 저장
        try {
            return paymentTransactionService.applyCancel(
                    memberId, paymentKey, request, plan, transactionKey, canceledAt);
        } catch (Exception postCancelEx) {
            log.error(
                    "토스 취소 성공 후 DB 반영 실패 (수동 확인 필요): paymentKey={}, error={}",
                    paymentKey,
                    postCancelEx.getMessage(),
                    postCancelEx);
            paymentTransactionService.recordManualRecoveryRequiredByPaymentKey(paymentKey);
            throw postCancelEx;
        }
    }

    private void validatePrepareRequest(PaymentPrepareRequest request) {
        boolean hasOrder = request.orderId() != null;
        boolean hasReservation = request.reservationId() != null;
        if ((hasOrder && hasReservation) || (!hasOrder && !hasReservation)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE, "스토어 주문 ID(orderId) 또는 영화 예매 ID(reservationId) 중 정확히 하나만 입력해야 합니다.");
        }
    }

    private LocalDateTime parseTossDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(dateTimeStr).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateTimeStr);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }
}
