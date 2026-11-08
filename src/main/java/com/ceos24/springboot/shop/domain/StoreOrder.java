package com.ceos24.springboot.shop.domain;

import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "store_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOrder {
//  사용자가 매점에서 구매한 내역

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id")
    private Theater theater;

    @Column(name = "total_amount")
    private Integer totalAmount;

    @CreationTimestamp
    @Column(name = "order_at", updatable = false)
    private LocalDateTime orderAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreOrderStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "payment_processing", nullable = false)
    private boolean paymentProcessing;

    @Builder
    public StoreOrder(
            User user,
            Theater theater,
            Integer totalAmount
    ) {
        this.user = user;
        this.theater = theater;
        this.totalAmount = totalAmount;
        this.status = StoreOrderStatus.PENDING;
        this.expiresAt = LocalDateTime.now().plusMinutes(10);
    }

    public void validatePayable(LocalDateTime now) {
        if (status == StoreOrderStatus.EXPIRED
                || (status == StoreOrderStatus.PENDING && expiresAt != null && !expiresAt.isAfter(now))) {
            throw new GlobalException(ErrorCode.STORE_ORDER_EXPIRED);
        }
        if (status != StoreOrderStatus.PENDING) {
            throw new GlobalException(ErrorCode.STORE_ORDER_NOT_PAYABLE);
        }
        if (paymentProcessing) {
            throw new GlobalException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        if (expiresAt == null) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_EXPIRATION);
        }
    }

    public void beginPayment(LocalDateTime now) {
        validatePayable(now);
        paymentProcessing = true;
    }

    public void completePayment() {
        validateProcessing();
        status = StoreOrderStatus.PAID;
        paymentProcessing = false;
    }

    public boolean cancel() {
        if (paymentProcessing) {
            throw new GlobalException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        if (status == StoreOrderStatus.PAID) {
            throw new GlobalException(ErrorCode.PAID_STORE_ORDER_CANCEL_NOT_SUPPORTED);
        }
        if (status != StoreOrderStatus.PENDING) {
            return false;
        }
        status = StoreOrderStatus.CANCELED;
        return true;
    }

    public boolean expireIfPending(LocalDateTime now) {
        if (paymentProcessing || status != StoreOrderStatus.PENDING
                || expiresAt == null || expiresAt.isAfter(now)) {
            return false;
        }
        status = StoreOrderStatus.EXPIRED;
        return true;
    }

    public void failPayment(LocalDateTime now) {
        validateProcessing();
        paymentProcessing = false;
        status = expiresAt != null && !expiresAt.isAfter(now)
                ? StoreOrderStatus.EXPIRED : StoreOrderStatus.CANCELED;
    }

    private void validateProcessing() {
        if (!paymentProcessing || status != StoreOrderStatus.PENDING) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    public void cancelAfterRefund() {
        if (status != StoreOrderStatus.PAID || paymentProcessing) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        status = StoreOrderStatus.CANCELED;
    }
}
