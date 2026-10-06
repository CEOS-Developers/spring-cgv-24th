package com.ceos.cgv.domain.reservation.dto;

import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record SeatHoldResponse(Long reservationId, Long screeningId,
                               ReservationStatus status, Instant expiresAt,
                               List<ReservedSeatResponse> seats) {
    public static SeatHoldResponse from(Reservation reservation) {
        return new SeatHoldResponse(reservation.getId(), reservation.getScreening().getId(),
                reservation.getStatus(), reservation.getExpiresAt(),
                reservation.getReservedSeats().stream().map(ReservedSeatResponse::from).toList());
    }
}
