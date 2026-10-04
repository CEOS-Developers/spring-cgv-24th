package com.ceos.cgv.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrderSeat {
    @Column(name = "reserved_seat_id", nullable = false)
    private Long reservedSeatId;

    @Column(name = "seat_row", nullable = false)
    private String seatRow;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Builder
    public PaymentOrderSeat(Long reservedSeatId, String seatRow, Integer seatNumber) {
        if (reservedSeatId == null || seatRow == null || seatNumber == null)
            throw new IllegalArgumentException("Seat snapshot is required");
        this.reservedSeatId = reservedSeatId;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;
    }

    public void cancel(Instant now) {
        if (canceledAt != null) throw new IllegalStateException("Seat already canceled");
        canceledAt = now;
    }
}
