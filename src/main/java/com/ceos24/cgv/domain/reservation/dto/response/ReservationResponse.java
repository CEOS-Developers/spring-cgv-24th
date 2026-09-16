package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ReservationResponse(
        Long reservationId,
        String movieTitle,
        String theaterName,
        String screenName,
        LocalDateTime startTime,
        String seatSummary,
        List<SeatInfoResponse> seats,
        Integer totalPrice,
        ReservationStatus status,
        Instant reservedAt
) {
    public static ReservationResponse of(Reservation reservation, List<SeatInfoResponse> seats) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getSchedule().getMovie().getTitle(),
                reservation.getSchedule().getScreen().getTheater().getName(),
                reservation.getSchedule().getScreen().getName(),
                reservation.getSchedule().getStartTime(),
                reservation.getSeatSummary(),
                seats,
                reservation.getTotalPrice(),
                reservation.getStatus(),
                reservation.getReservedAt()
        );
    }
}
