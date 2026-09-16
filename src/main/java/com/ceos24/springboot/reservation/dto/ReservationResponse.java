package com.ceos24.springboot.reservation.dto;

import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long reservationId,
        Long screeningId,
        Integer childCount,
        Integer youthCount,
        Integer adultCount,
        Integer seniorCount,
        String seatNumbers,
        Integer totalPrice,
        ReservationStatus status,
        LocalDateTime reservationAt
) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getScreening().getScreeningId(),
                reservation.getChildCount(),
                reservation.getYouthCount(),
                reservation.getAdultCount(),
                reservation.getSeniorCount(),
                reservation.getSeatNumbers(),
                reservation.getTotalPrice(),
                reservation.getStatus(),
                reservation.getReservationAt()
        );
    }
}