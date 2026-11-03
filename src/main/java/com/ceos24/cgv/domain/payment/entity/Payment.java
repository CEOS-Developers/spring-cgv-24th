package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_purchase", columnNames = "purchase_id"),
        @UniqueConstraint(name = "uk_payment_reservation", columnNames = "reservation_id"),
        @UniqueConstraint(name = "uk_payment_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_payment_key", columnNames = "payment_key")
}, check = @CheckConstraint(name = "ck_payment_target", constraint =
        "(target_type = 'PURCHASE' and purchase_id is not null and reservation_id is null) "
                + "or (target_type = 'RESERVATION' and reservation_id is not null and purchase_id is null)"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private PaymentTargetType targetType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", updatable = false)
    private Purchase purchase;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", updatable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false, updatable = false)
    private UserEntity payer;

    @Column(name = "order_id", nullable = false, length = 64, updatable = false)
    private String orderId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private long totalAmount;

    @Column(name = "canceled_amount", nullable = false)
    private long canceledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(length = 50)
    private String method;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    public static Payment create(Purchase purchase) {
        if (purchase == null || purchase.getTotalAmount() <= 0) {
            throw new IllegalArgumentException("금액이 확정된 주문이 필요합니다.");
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 주문에만 결제 정보를 생성할 수 있습니다.");
        }

        Payment payment = create(PaymentTargetType.PURCHASE, purchase.getUserEntity(),
                purchase.getOrderId(), purchase.getTotalAmount(), purchase.getExpiresAt());
        payment.purchase = purchase;
        return payment;
    }

    // 예매 생성 서비스가 계산한 금액과 유효시간을 같은 트랜잭션에서 저장한다.
    public static Payment createForReservation(Reservation reservation, long totalAmount, LocalDateTime expiresAt) {
        if (reservation == null) {
            throw new IllegalArgumentException("영화 예매 정보는 필수입니다.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 예매에만 결제 정보를 생성할 수 있습니다.");
        }
        Payment payment = create(PaymentTargetType.RESERVATION, reservation.getUserEntity(),
                "cgv_reservation_" + UUID.randomUUID(), totalAmount, expiresAt);
        payment.reservation = reservation;
        return payment;
    }

    private static Payment create(PaymentTargetType targetType, UserEntity payer, String orderId,
                                  long totalAmount, LocalDateTime expiresAt) {
        if (payer == null || totalAmount <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("결제자, 양수인 확정 금액, 주문 유효시간이 필요합니다.");
        }
        requireText(orderId, 64, "주문번호");
        Payment payment = new Payment();
        payment.targetType = targetType;
        payment.payer = payer;
        payment.orderId = orderId;
        payment.totalAmount = totalAmount;
        payment.expiresAt = expiresAt;
        payment.status = PaymentStatus.READY;
        payment.createdAt = Instant.now();
        return payment;
    }

    public Long getTargetId() {
        return switch (targetType) {
            case PURCHASE -> purchase.getId();
            case RESERVATION -> reservation.getId();
        };
    }

    // PG 승인 성공을 확인한 트랜잭션에서만 호출한다.
    public void approve(String paymentKey, String method, Instant approvedAt) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("이미 승인된 결제는 다시 승인할 수 없습니다.");
        }
        requireText(paymentKey, 200, "결제 키");
        requireText(method, 50, "결제수단");
        if (approvedAt == null) {
            throw new IllegalArgumentException("승인 시각은 필수입니다.");
        }

        this.paymentKey = paymentKey;
        this.method = method;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.DONE;
    }

    public void validateCancelable(long amount) {
        if (status != PaymentStatus.DONE && status != PaymentStatus.PARTIAL_CANCELED) {
            throw new IllegalStateException("취소할 수 있는 승인 결제가 아닙니다.");
        }
        if (amount <= 0 || amount > getRemainingAmount()) {
            throw new IllegalArgumentException("취소 금액은 0보다 크고 남은 금액 이하여야 합니다.");
        }
    }

    // PG 취소 성공을 확인한 트랜잭션에서만 호출한다.
    public void cancel(long amount) {
        validateCancelable(amount);
        canceledAmount += amount;
        status = getRemainingAmount() == 0 ? PaymentStatus.CANCELED : PaymentStatus.PARTIAL_CANCELED;
    }

    public long getRemainingAmount() {
        return totalAmount - canceledAmount;
    }

    public void abort() {
        closePending(PaymentStatus.ABORTED);
    }

    public void expire() {
        closePending(PaymentStatus.EXPIRED);
    }

    private void closePending(PaymentStatus status) {
        if (this.status != PaymentStatus.READY) {
            throw new IllegalStateException("승인 전 결제만 종료할 수 있습니다.");
        }
        this.status = status;
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " 값이 없거나 허용 길이를 초과했습니다.");
        }
    }
}
