package com.ceos.cgv.domain.payment.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationPaymentOrderSnapshot {
    @Column(name = "reservation_id", unique = true)
    private Long reservationId;

    @Column(name = "seat_price")
    private Long seatPrice;

    @ElementCollection
    @CollectionTable(
            name = "payment_order_seat",
            joinColumns = @JoinColumn(name = "payment_order_id"))
    @OrderColumn(name = "seat_index")
    private List<PaymentOrderSeat> seats;

    ReservationPaymentOrderSnapshot(Long reservationId, long seatPrice, List<PaymentOrderSeat> seats) {
        if (reservationId == null || seatPrice <= 0 || seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Complete order snapshot is required");
        }
        this.reservationId = reservationId;
        this.seatPrice = seatPrice;
        this.seats = new ArrayList<>(seats);
    }

    public List<PaymentOrderSeat> getSeats() {
        return Collections.unmodifiableList(seats);
    }

    public void cancelSeats(List<Long> ids, Instant now) {
        for (PaymentOrderSeat seat : seats) {
            if (ids.contains(seat.getReservedSeatId())) {
                seat.cancel(now);
            }
        }
    }

    long totalAmount() {
        return Math.multiplyExact(seatPrice, seats.size());
    }
}
