package com.ceos.cgv.domain.reservation.service.payment;

import lombok.Builder;
import java.time.Instant;

@Builder
public record PaymentReservationSeat(Long reservedSeatId, String seatRow, Integer seatNumber,
                                     Instant canceledAt) {
}
