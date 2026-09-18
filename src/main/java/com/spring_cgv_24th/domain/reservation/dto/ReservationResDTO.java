package com.spring_cgv_24th.domain.reservation.dto;

import com.spring_cgv_24th.domain.reservation.entity.Reservation;
import com.spring_cgv_24th.domain.reservation.enums.ReservationStatus;
import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;
import java.time.LocalDateTime;
import java.util.List;

public record ReservationResDTO(
        Long reservationId,
        Long screeningId,
        ReservationStatus status,
        LocalDateTime reservedAt,
        List<Long> screeningSeatIds,
        Long totalPrice
) {

    public static ReservationResDTO from(Reservation reservation, List<ScreeningSeat> seats) {
        return new ReservationResDTO(
                reservation.getId(),
                reservation.getScreening().getId(),
                reservation.getStatus(),
                reservation.getReservedAt(),
                seats.stream().map(ScreeningSeat::getId).toList(),
                reservation.getTotalPrice());
    }
}
