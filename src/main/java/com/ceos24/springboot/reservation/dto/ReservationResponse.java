package com.ceos24.springboot.reservation.dto;

import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.theater.domain.Seat;

import java.time.LocalDateTime;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long screeningId,

        List<SeatResponse> seats,

        Integer childCount,
        Integer youthCount,
        Integer adultCount,
        Integer seniorCount,

        Integer totalPrice,
        ReservationStatus status,
        LocalDateTime reservationAt
) {

    public static ReservationResponse from(Reservation reservation, List<Seat> seats) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getScreening().getScreeningId(),
                seats.stream()
                        .map(SeatResponse::from)
                        .toList(),
                reservation.getChildCount(),
                reservation.getYouthCount(),
                reservation.getAdultCount(),
                reservation.getSeniorCount(),
                reservation.getTotalPrice(),
                reservation.getStatus(),
                reservation.getReservationAt()
        );
    }
}