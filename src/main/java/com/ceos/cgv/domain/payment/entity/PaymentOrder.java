package com.ceos.cgv.domain.payment.entity;

import com.ceos.cgv.domain.payment.enums.PaymentTargetType;
import com.ceos.cgv.global.entity.BaseTimeEntity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Table(
        name = "payment_order",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_payment_order_food_order_id", columnNames = "food_order_id"),
        check =
                @CheckConstraint(
                        name = "chk_payment_order_target",
                        constraint = """
                                (reservation_id IS NOT NULL AND food_order_id IS NULL AND seat_price IS NOT NULL AND seat_price > 0)
                                OR (reservation_id IS NULL AND food_order_id IS NOT NULL AND seat_price IS NULL AND total_amount > 0)
                                """))
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

    @Column(name = "food_order_id")
    private Long foodOrderId;

    @Builder
    public PaymentOrder(
            String orderId,
            Long reservationId,
            Long userId,
            String orderName,
            long seatPrice,
            Instant expiresAt,
            List<PaymentOrderSeat> seats) {
        this(orderId, userId, orderName, expiresAt);
        this.reservationSnapshot = new ReservationPaymentOrderSnapshot(reservationId, seatPrice, seats);
        this.totalAmount = reservationSnapshot.totalAmount();
    }

    private PaymentOrder(String orderId, Long userId, String orderName, Instant expiresAt) {
        if (orderId == null || userId == null || orderName == null || expiresAt == null) {
            throw new IllegalArgumentException("Complete order snapshot is required");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.orderName = orderName;
        this.expiresAt = expiresAt;
    }

    public static PaymentOrder forFoodOrder(
            String orderId,
            Long foodOrderId,
            Long userId,
            String orderName,
            long totalAmount,
            Instant expiresAt) {
        if (foodOrderId == null || totalAmount <= 0) {
            throw new IllegalArgumentException("Food order and positive amount are required");
        }
        PaymentOrder order = new PaymentOrder(orderId, userId, orderName, expiresAt);
        order.foodOrderId = foodOrderId;
        order.totalAmount = totalAmount;
        return order;
    }

    public PaymentTargetType getTargetType() {
        return foodOrderId == null ? PaymentTargetType.RESERVATION : PaymentTargetType.FOOD_ORDER;
    }

    public Long getReservationId() {
        return foodOrderId == null ? reservationSnapshot.getReservationId() : null;
    }

    public Long getSeatPrice() {
        return foodOrderId == null ? reservationSnapshot.getSeatPrice() : null;
    }

    public List<PaymentOrderSeat> getSeats() {
        return foodOrderId == null ? reservationSnapshot.getSeats() : List.of();
    }

    public void cancelSeats(List<Long> ids, Instant now) {
        if (foodOrderId != null) {
            throw new IllegalStateException("Food orders do not have seats");
        }
        reservationSnapshot.cancelSeats(ids, now);
    }
}
