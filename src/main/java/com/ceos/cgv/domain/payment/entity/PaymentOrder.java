package com.ceos.cgv.domain.payment.entity;

import com.ceos.cgv.global.entity.BaseTimeEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_name", nullable = false)
    private String orderName;

    @Column(name = "seat_price", nullable = false)
    private long seatPrice;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection
    @CollectionTable(
            name = "payment_order_seat",
            joinColumns = @JoinColumn(name = "payment_order_id"))
    @OrderColumn(name = "seat_index")
    private List<PaymentOrderSeat> seats = new ArrayList<>();

    @Builder
    public PaymentOrder(
            String orderId,
            Long reservationId,
            Long userId,
            String orderName,
            long seatPrice,
            Instant expiresAt,
            List<PaymentOrderSeat> seats) {
        if (orderId == null
                || reservationId == null
                || userId == null
                || orderName == null
                || expiresAt == null
                || seatPrice <= 0
                || seats == null
                || seats.isEmpty())
            throw new IllegalArgumentException("Complete order snapshot is required");
        this.orderId = orderId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.orderName = orderName;
        this.seatPrice = seatPrice;
        this.totalAmount = Math.multiplyExact(seatPrice, seats.size());
        this.expiresAt = expiresAt;
        this.seats.addAll(seats);
    }

    public List<PaymentOrderSeat> getSeats() {
        return Collections.unmodifiableList(seats);
    }

    public void cancelSeats(List<Long> ids, Instant now) {
        for (PaymentOrderSeat seat : seats)
            if (ids.contains(seat.getReservedSeatId())) seat.cancel(now);
    }
}
