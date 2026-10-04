package com.ceos.cgv.domain.reservation.service.payment;

import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Builder
public record PaymentReservationSnapshot(Long reservationId, Long userId, Long screeningId,
                                         String movieTitle, ReservationStatus status,
                                         Instant expiresAt, List<PaymentReservationSeat> seats) {
    public PaymentReservationSnapshot {
        seats = List.copyOf(seats);
    }
}
