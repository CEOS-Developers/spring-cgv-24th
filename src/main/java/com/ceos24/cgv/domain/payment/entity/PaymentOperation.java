package com.ceos24.cgv.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_operation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_operation_request",
                columnNames = {"payment_id", "operation_type", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_operation_pg_key", columnNames = "pg_idempotency_key"),
        @UniqueConstraint(name = "uk_payment_operation_transaction", columnNames = "pg_transaction_key")
}, indexes = @Index(name = "idx_payment_operation_payment_status", columnList = "payment_id,status"))
public class PaymentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20, updatable = false)
    private PaymentOperationType type;

    @Column(name = "idempotency_key", nullable = false, length = 300, updatable = false)
    private String idempotencyKey;

    // 결제별로 범위가 나뉜 클라이언트 키를 그대로 PG에 보내지 않고, 작업마다 별도 키를 사용한다.
    @Column(name = "pg_idempotency_key", nullable = false, length = 36, updatable = false)
    private String pgIdempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    // 승인 결과가 불명확해도 PG를 재조회할 수 있도록 시도한 결제 키를 보존한다.
    @Column(name = "payment_key", nullable = false, length = 200, updatable = false)
    private String paymentKey;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "cancel_reason", length = 200, updatable = false)
    private String cancelReason;

    @Column(name = "full_cancellation", nullable = false, updatable = false)
    private boolean fullCancellation;

    @Column(name = "before_canceled_amount", nullable = false, updatable = false)
    private long beforeCanceledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentOperationStatus status;

    @Column(name = "pg_transaction_key", length = 64)
    private String pgTransactionKey;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_payment_status", length = 30)
    private PaymentStatus resultPaymentStatus;

    @Column(name = "result_canceled_amount")
    private Long resultCanceledAmount;

    @Version
    private long version;

    public static PaymentOperation startConfirm(Payment payment, String paymentKey, String idempotencyKey) {
        if (payment == null || payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalStateException("승인을 시작할 수 있는 결제가 아닙니다.");
        }
        return start(payment, PaymentOperationType.CONFIRM, paymentKey,
                payment.getTotalAmount(), null, idempotencyKey, false);
    }

    public static PaymentOperation startCancel(Payment payment, long amount,
                                               String cancelReason, String idempotencyKey) {
        if (payment == null) {
            throw new IllegalArgumentException("결제 정보는 필수입니다.");
        }
        payment.validateCancelable(amount);
        requireText(cancelReason, 200, "취소 사유");
        return start(payment, PaymentOperationType.CANCEL, payment.getPaymentKey(),
                amount, cancelReason, idempotencyKey, false);
    }

    public static PaymentOperation startFullCancel(Payment payment, String reason, String idempotencyKey) {
        if (payment == null) {
            throw new IllegalArgumentException("결제 정보는 필수입니다.");
        }
        payment.validateCancelable(payment.getRemainingAmount());
        requireText(reason, 200, "취소 사유");
        return start(payment, PaymentOperationType.CANCEL, payment.getPaymentKey(),
                payment.getRemainingAmount(), reason, idempotencyKey, true);
    }

    private static PaymentOperation start(Payment payment, PaymentOperationType type, String paymentKey,
                                          long amount, String cancelReason, String idempotencyKey, boolean fullCancellation) {
        requireText(paymentKey, 200, "결제 키");
        requireText(idempotencyKey, 300, "멱등키");

        PaymentOperation operation = new PaymentOperation();
        operation.payment = payment;
        operation.type = type;
        operation.paymentKey = paymentKey;
        operation.amount = amount;
        operation.cancelReason = cancelReason;
        operation.fullCancellation = fullCancellation;
        operation.beforeCanceledAmount = payment.getCanceledAmount();
        operation.idempotencyKey = idempotencyKey;
        operation.pgIdempotencyKey = UUID.randomUUID().toString();
        operation.requestHash = operation.hashRequest(type, paymentKey, amount, cancelReason);
        operation.status = PaymentOperationStatus.PROCESSING;
        operation.requestedAt = timestamp();
        operation.lastAttemptAt = operation.requestedAt;
        operation.attemptNumber = 1;
        return operation;
    }

    public boolean matchesRequest(PaymentOperationType type, String paymentKey, long amount, String cancelReason) {
        return requestHash.equals(hashRequest(type, paymentKey, amount, cancelReason));
    }

    public boolean matchesCancelRequest(Long requestedAmount, String reason) {
        return type == PaymentOperationType.CANCEL && fullCancellation == (requestedAmount == null)
                && matchesRequest(PaymentOperationType.CANCEL, paymentKey,
                requestedAmount == null ? amount : requestedAmount, reason);
    }

    public void retry() {
        requireUnresolved();
        status = PaymentOperationStatus.PROCESSING;
        lastAttemptAt = timestamp();
        attemptNumber = Math.incrementExact(attemptNumber);
    }

    public void captureResult() {
        if (status != PaymentOperationStatus.SUCCEEDED) {
            throw new IllegalStateException("성공한 작업만 응답을 보관할 수 있습니다.");
        }
        resultPaymentStatus = payment.getStatus();
        resultCanceledAmount = payment.getCanceledAmount();
    }

    public void succeed(String pgTransactionKey) {
        requireUnresolved();
        if (pgTransactionKey != null) {
            requireText(pgTransactionKey, 64, "PG 거래 키");
        }
        this.pgTransactionKey = pgTransactionKey;
        this.status = PaymentOperationStatus.SUCCEEDED;
        this.completedAt = timestamp();
    }

    public void fail(String errorCode, String errorMessage) {
        requireUnresolved();
        recordError(errorCode, errorMessage);
        this.status = PaymentOperationStatus.FAILED;
        this.completedAt = timestamp();
    }

    public void markUnknown(String errorCode, String errorMessage) {
        requireUnresolved();
        recordError(errorCode, errorMessage);
        this.status = PaymentOperationStatus.UNKNOWN;
    }

    public boolean isUnresolved() {
        return status == PaymentOperationStatus.PROCESSING || status == PaymentOperationStatus.UNKNOWN;
    }

    private void requireUnresolved() {
        if (!isUnresolved()) {
            throw new IllegalStateException("이미 결과가 확정된 결제 작업입니다.");
        }
    }

    private void recordError(String errorCode, String errorMessage) {
        requireText(errorCode, 100, "오류 코드");
        if (errorMessage != null && errorMessage.length() > 1000) {
            throw new IllegalArgumentException("오류 메시지가 허용 길이를 초과했습니다.");
        }
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    private String hashRequest(PaymentOperationType type, String paymentKey, long amount, String cancelReason) {
        // 필드 길이를 함께 기록해 구분자나 줄바꿈이 들어가도 요청을 모호하게 합치지 않는다.
        StringBuilder request = new StringBuilder();
        for (String field : new String[]{type == null ? null : type.name(),
                payment.getOrderId(), paymentKey, Long.toString(amount), cancelReason}) {
            request.append(field == null ? -1 : field.length()).append(':');
            if (field != null) {
                request.append(field);
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " 값이 없거나 허용 길이를 초과했습니다.");
        }
    }

    private static Instant timestamp() {
        // DB의 TIMESTAMP(6) 정밀도와 맞춰 최초 응답과 재전송 응답의 시각도 같게 보존한다.
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
