package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(
        name = "purchase",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_order_id", columnNames = "order_id")
)
public class Purchase {

    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity userEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(name = "order_id", nullable = false, length = 64, updatable = false)
    private String orderId;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseStatus status;

    // 기존 생성 시각 데이터를 보존하기 위해 DB 컬럼명은 유지한다.
    @Column(name = "purchased_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    public static Purchase create(UserEntity userEntity, Cinema cinema, long totalAmount) {
        if (userEntity == null || cinema == null) {
            throw new IllegalArgumentException("사용자와 영화관은 필수입니다.");
        }
        if (totalAmount <= 0) {
            throw new IllegalArgumentException("주문 금액은 0보다 커야 합니다.");
        }

        Purchase purchase = new Purchase();
        purchase.userEntity = userEntity;
        purchase.cinema = cinema;
        purchase.orderId = "cgv_store_" + UUID.randomUUID();
        purchase.totalAmount = totalAmount;
        purchase.status = PurchaseStatus.PENDING_PAYMENT;
        purchase.createdAt = LocalDateTime.now();
        purchase.expiresAt = purchase.createdAt.plus(PAYMENT_TIMEOUT);
        return purchase;
    }

    public void markPaid() {
        requirePending();
        status = PurchaseStatus.PAID;
    }

    public void markPartiallyCanceled() {
        if (status != PurchaseStatus.PAID && status != PurchaseStatus.PARTIALLY_CANCELED) {
            throw new IllegalStateException("결제된 주문만 부분 취소할 수 있습니다.");
        }
        status = PurchaseStatus.PARTIALLY_CANCELED;
    }

    public void cancel() {
        if (status != PurchaseStatus.PENDING_PAYMENT && status != PurchaseStatus.PAID
                && status != PurchaseStatus.PARTIALLY_CANCELED) {
            throw new IllegalStateException("취소할 수 있는 주문이 아닙니다.");
        }
        status = PurchaseStatus.CANCELED;
    }

    public void expire() {
        requirePending();
        status = PurchaseStatus.EXPIRED;
    }

    private void requirePending() {
        if (status != PurchaseStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 주문이 아닙니다.");
        }
    }
}
