package com.spring_cgv_24th.domain.store.entity;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.payment.target.PaymentTarget;
import com.spring_cgv_24th.domain.store.enums.StoreOrderStatus;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "store_order", indexes = @Index(name = "idx_store_order_expiration", columnList = "status, expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOrder implements PaymentTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_store_order_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false, foreignKey = @ForeignKey(name = "fk_store_order_theater"))
    private Theater theater;

    @Column(name = "purchased_at", nullable = false, updatable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime purchasedAt;

    // 기존 주문은 완료로 보존하고, 새 주문의 상태는 생성자에서 지정한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30) default 'COMPLETED'")
    private StoreOrderStatus status;

    @Column(name = "expires_at", updatable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime expiresAt;

    @Column(name = "payment_order_id", unique = true, updatable = false, length = 64)
    private String paymentOrderId;

    @Builder
    public StoreOrder(Member member, Theater theater, LocalDateTime purchasedAt) {
        this.member = member;
        this.theater = theater;
        this.purchasedAt = purchasedAt;
        this.status = StoreOrderStatus.PENDING_PAYMENT;
        this.expiresAt = purchasedAt.plusMinutes(10);
        this.paymentOrderId = UUID.randomUUID().toString();
    }

    public void beginPayment(LocalDateTime now) {
        if (status != StoreOrderStatus.PENDING_PAYMENT || !now.isBefore(expiresAt)) {
            throw new CustomException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }
        status = StoreOrderStatus.PAYMENT_PROCESSING;
    }

    public void completePayment() {
        status = StoreOrderStatus.COMPLETED;
    }

    public void failPayment() {
        status = StoreOrderStatus.PAYMENT_FAILED;
    }

    public void validatePaymentCancellation(LocalDateTime now) {
        if (status != StoreOrderStatus.COMPLETED && status != StoreOrderStatus.PARTIALLY_CANCELLED) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELLABLE);
        }
    }

    public void cancelPayment() {
        status = StoreOrderStatus.CANCELLED;
    }

    public void partiallyCancelPayment() {
        status = StoreOrderStatus.PARTIALLY_CANCELLED;
    }

    public boolean releaseStock(LocalDateTime now) {
        if (status == StoreOrderStatus.PAYMENT_FAILED) {
            status = StoreOrderStatus.CANCELLED;
            return true;
        }
        if (status != StoreOrderStatus.PENDING_PAYMENT || now.isBefore(expiresAt)) {
            return false;
        }
        status = StoreOrderStatus.EXPIRED;
        return true;
    }
}
