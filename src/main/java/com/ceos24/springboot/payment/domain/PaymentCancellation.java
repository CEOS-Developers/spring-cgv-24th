package com.ceos24.springboot.payment.domain;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.dto.PaymentCancelRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_cancellation", indexes = @Index(name = "idx_cancellation_recovery",
        columnList = "status,next_recovery_at,cancellation_id"), uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_cancel_request", columnNames = {"payment_id", "request_id"}),
        @UniqueConstraint(name = "uk_payment_cancel_transaction", columnNames = "transaction_key"),
        @UniqueConstraint(name = "uk_payment_cancel_idempotency", columnNames = "pg_idempotency_key")
})
public class PaymentCancellation {
    @Embedded
    private PaymentRecovery recovery = new PaymentRecovery();

    // PG의 명시적 거절도 조회로 미변경을 확인한 뒤에만 FAILED로 확정한다.
    @Column(name = "rejected_by_pg", nullable = false)
    private boolean rejectedByPg;

    public void recordRejection() { rejectedByPg = true; }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancellation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    // 사용자 요청 키는 결제별로 관리하고, PG에는 별도의 UUID를 사용한다.
    @Column(name = "request_id", nullable = false, updatable = false, length = 64)
    private String requestId;

    @Column(name = "pg_idempotency_key", nullable = false, updatable = false, length = 36)
    private String pgIdempotencyKey;

    // null은 '남은 금액 전체 취소' 요청. 재시도 시 원본 요청과 비교한다.
    @Column(name = "requested_amount", updatable = false)
    private Long requestedAmount;

    @Column(name = "cancel_amount", nullable = false, updatable = false)
    private Long cancelAmount;

    @Column(name = "balance_before", nullable = false, updatable = false)
    private Long balanceBefore;

    @Column(name = "cancel_reason", nullable = false, updatable = false, length = 200)
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentCancellationStatus status;

    @Column(name = "transaction_key", length = 64)
    private String transactionKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public static PaymentCancellation start(Payment payment, String requestId, PaymentCancelRequest request, LocalDateTime now) {
        PaymentCancellation result = new PaymentCancellation();
        result.payment = payment;
        result.requestId = requestId;
        result.pgIdempotencyKey = UUID.randomUUID().toString();
        result.requestedAmount = request.cancelAmount();
        result.cancelAmount = request.cancelAmount() == null ? payment.getBalanceAmount() : request.cancelAmount();
        result.balanceBefore = payment.getBalanceAmount();
        result.cancelReason = request.cancelReason();
        result.status = PaymentCancellationStatus.PROCESSING;
        result.createdAt = Objects.requireNonNull(now, "now");
        return result;
    }

    public void validateSameRequest(PaymentCancelRequest request) {
        if (!Objects.equals(requestedAmount, request.cancelAmount())
                || !Objects.equals(cancelReason, request.cancelReason())) {
            throw new GlobalException(ErrorCode.PAYMENT_CANCEL_REQUEST_MISMATCH);
        }
    }

    public boolean isPending() {
        return status == PaymentCancellationStatus.PROCESSING || status == PaymentCancellationStatus.UNKNOWN;
    }

    public void complete(String transactionKey, LocalDateTime canceledAt) {
        this.transactionKey = transactionKey;
        this.canceledAt = canceledAt;
        this.status = PaymentCancellationStatus.DONE;
    }

    public void markUnknown() { this.status = PaymentCancellationStatus.UNKNOWN; }

    public void markFailed() { this.status = PaymentCancellationStatus.FAILED; }
}
