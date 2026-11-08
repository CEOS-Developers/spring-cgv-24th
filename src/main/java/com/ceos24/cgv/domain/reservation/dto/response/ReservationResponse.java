package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;

import java.util.List;

public record ReservationResponse(
        Long reservationId,
        Long userId,
        Long screeningId,
        String orderId,
        Integer totalPrice,
        ReservationStatus status,
        List<ReservationSeatResponse> seats
) {

    // 결제 주문번호를 포함하는 응답
    public static ReservationResponse from(
            Reservation reservation,
            String orderId
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
                orderId,
                reservation.getTotalPrice(),
                reservation.getStatus(),
                seatResponses
        );
    }


    public static ReservationResponse from(Reservation reservation) {
        return from(reservation, null);
    }
}