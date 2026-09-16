package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;

import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long userId,
        Long screeningId,
        Integer totalPrice,
        ReservationStatus status,
        List<ReservationSeatResponse> seats
) {

    public static ReservationResponse from(
            Reservation reservation
    ) {
        List<ReservationSeatResponse> seatResponses =
                reservation.getReservationSeats()
                        .stream()
                        .map(ReservationSeatResponse::from)
                        .toList();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getScreening().getId(),
                reservation.getTotalPrice(),
                reservation.getStatus(),
                seatResponses
        );
    }
}