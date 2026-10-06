package com.ceos.cgv.domain.reservation.dto;

import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long userId,
        Long screeningId,
        ReservationStatus status,
        LocalDateTime createdAt,
        Instant expiresAt,
        List<ReservedSeatResponse> seats
) {
    public static ReservationResponse from(Reservation reservation) {
        return withStatus(reservation, reservation.getStatus());
    }

    public static ReservationResponse from(Reservation reservation, Instant now) {
        return withStatus(reservation, reservation.isExpiredAt(now)
                ? ReservationStatus.EXPIRED : reservation.getStatus());
    }

    private static ReservationResponse withStatus(Reservation reservation, ReservationStatus status) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getScreening().getId(),
                status,
                reservation.getCreatedAt(),
                reservation.getExpiresAt(),
                reservation.getReservedSeats().stream()
                        .map(ReservedSeatResponse::from)
                        .toList()
        );
    }
}
