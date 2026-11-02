package com.ceos24.springboot.payment.domain;

import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "payment", uniqueConstraints = {@UniqueConstraint(name = "uk_payment_order_id", columnNames = "order_id"),
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
            throw new IllegalArgumentException("결제 사용자가 필요합니다.");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("결제 대상 유형이 필요합니다.");
        }
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException("유효한 결제 대상 ID가 필요합니다.");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
        if (now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "결제 만료 시각은 생성 시각보다 이후여야 합니다."
            );
        }

        this.orderId = "pay_" + UUID.randomUUID();
        this.user = user;
        this.targetType = targetType;
        this.targetId = targetId;
        this.amount = amount;
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
}