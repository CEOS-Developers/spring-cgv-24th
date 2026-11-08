package com.ceos24.springboot.payment.domain;

import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "payment", indexes = @Index(name = "idx_payment_user_status_approved",
        columnList = "user_id,status,approved_at,payment_id"),
        uniqueConstraints = {@UniqueConstraint(name = "uk_payment_order_id", columnNames = "order_id"),
                @UniqueConstraint(name = "uk_payment_payment_key", columnNames = "payment_key")}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // 토스에 전달하는 주문번호. 예매 ID나 매점 주문 ID와 별개.
    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private PaymentTargetType targetType;

    // RESERVATION이면 reservationId, STORE_ORDER이면 매점 주문 ID
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    // 클라이언트가 보낸 금액이 아닌 서버에서 계산한 최종 금액
    @Column(name = "amount", nullable = false, updatable = false)
    private Long amount;

    // 최초 금액은 보존하고, 승인/환불이 검증됐을 때만 잔액을 변경한다.
    @Column(name = "balance_amount", nullable = false)
    private Long balanceAmount;

    // Merchant-side order-name snapshot; do not depend on current movie/menu data.
    @Column(name = "order_name", nullable = false, updatable = false, length = 100)
    private String orderName;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    // Verified PG approval metadata; unknown for payments approved before this field existed.
    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    // 결제 준비 시에는 null. 인증 후 승인 처리 과정에서 저장한다.
    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    // 동일한 승인 요청을 재시도할 때 같은 값을 사용한다.
    @Column(name = "confirm_idempotency_key",
            nullable = false,
            updatable = false,
            length = 36)
    private String confirmIdempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private Payment(
            User user,
            PaymentTargetType targetType,
            Long targetId,
            Long amount,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        if (user == null) {
            throw new GlobalException(ErrorCode.PAYMENT_USER_REQUIRED);
        }

        if (targetType == null) {
            throw new GlobalException(ErrorCode.PAYMENT_TARGET_TYPE_REQUIRED);
        }

        if (targetId == null || targetId <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_TARGET_ID);
        }

        if (amount == null || amount <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }

        if (now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_EXPIRATION);
        }

        this.orderId = "pay_" + UUID.randomUUID();
        this.user = user;
        this.targetType = targetType;
        this.targetId = targetId;
        this.amount = amount;
        this.balanceAmount = 0L;
        this.orderName = targetType == PaymentTargetType.RESERVATION ? "영화 티켓 예매" : "매점 상품 주문";
        this.currency = "KRW";
        this.status = PaymentStatus.READY;
        this.confirmIdempotencyKey = UUID.randomUUID().toString();
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public static Payment prepare(
            User user,
            PaymentTargetType targetType,
            Long targetId,
            Long amount,
            LocalDateTime now,
            LocalDateTime expiresAt
    ) {
        return new Payment(
                user,
                targetType,
                targetId,
                amount,
                now,
                expiresAt
        );
    }

    // 결제 상태 변경
    public void startProcessing(String paymentKey) {
        if (status != PaymentStatus.READY
                && status != PaymentStatus.UNKNOWN) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        if (paymentKey == null || paymentKey.isBlank()
                || paymentKey.length() > 200) {
            throw new GlobalException(
                    ErrorCode.INVALID_PAYMENT_CONFIRM_REQUEST
            );
        }

        if (this.paymentKey != null
                && !this.paymentKey.equals(paymentKey)) {
            throw new GlobalException(
                    ErrorCode.PAYMENT_INFORMATION_MISMATCH
            );
        }

        this.paymentKey = paymentKey;
        this.status = PaymentStatus.PROCESSING;
    }

    public void complete(LocalDateTime approvedAt, String paymentMethod) {
        validateProcessingState();

        if (approvedAt == null) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        this.approvedAt = approvedAt;
        this.paymentMethod = paymentMethod;
        this.balanceAmount = amount;
        this.status = PaymentStatus.DONE;
    }

    public void validateCancelable() {
        if (status == PaymentStatus.CANCELED && approvedAt != null) {
            throw new GlobalException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }
        if ((status != PaymentStatus.DONE && status != PaymentStatus.PARTIAL_CANCELED)
                || approvedAt == null || balanceAmount == null || balanceAmount <= 0) {
            throw new GlobalException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }
    }

    public void applyCancellation(long cancelAmount) {
        validateCancelable();
        if (cancelAmount <= 0 || cancelAmount > balanceAmount) {
            throw new GlobalException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }
        balanceAmount -= cancelAmount;
        status = balanceAmount == 0 ? PaymentStatus.CANCELED : PaymentStatus.PARTIAL_CANCELED;
    }

    public long getCanceledAmount() {
        return approvedAt == null ? 0L : amount - balanceAmount;
    }

    public void markFailed() {
        validateProcessingState();
        this.status = PaymentStatus.FAILED;
    }

    public void markUnknown() {
        validateProcessingState();
        this.status = PaymentStatus.UNKNOWN;
    }

    public void closePending(boolean expired) {
        if (status != PaymentStatus.READY) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        status = expired ? PaymentStatus.EXPIRED : PaymentStatus.CANCELED;
    }

    private void validateProcessingState() {
        if (status != PaymentStatus.PROCESSING
                && status != PaymentStatus.UNKNOWN) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }
}
