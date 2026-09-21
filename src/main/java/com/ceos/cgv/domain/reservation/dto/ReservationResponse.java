package com.ceos.cgv.domain.reservation.dto;

import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long userId,
        Long screeningId,
        ReservationStatus status,
        LocalDateTime createdAt,
        List<ReservedSeatResponse> seats
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getScreening().getId(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getReservedSeats().stream()
                        .map(ReservedSeatResponse::from)
                        .toList()
        );
    }
}
