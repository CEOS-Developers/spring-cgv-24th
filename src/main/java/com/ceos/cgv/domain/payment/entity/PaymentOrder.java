package com.ceos.cgv.domain.payment.entity;

import com.ceos.cgv.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "payment_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_name", nullable = false)
    private String orderName;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Embedded
    private ReservationPaymentOrderSnapshot reservationSnapshot;

    @Builder
    public PaymentOrder(
            String orderId,
            Long reservationId,
            Long userId,
            String orderName,
            long seatPrice,
            Instant expiresAt,
            List<PaymentOrderSeat> seats) {
        if (orderId == null || userId == null || orderName == null || expiresAt == null) {
            throw new IllegalArgumentException("Complete order snapshot is required");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.orderName = orderName;
        this.expiresAt = expiresAt;
        this.reservationSnapshot = new ReservationPaymentOrderSnapshot(reservationId, seatPrice, seats);
        this.totalAmount = reservationSnapshot.totalAmount();
    }

    public Long getReservationId() {
        return reservationSnapshot.getReservationId();
    }

    public long getSeatPrice() {
        return reservationSnapshot.getSeatPrice();
    }

    public List<PaymentOrderSeat> getSeats() {
        return reservationSnapshot.getSeats();
    }

    public void cancelSeats(List<Long> ids, Instant now) {
        reservationSnapshot.cancelSeats(ids, now);
    }
}
