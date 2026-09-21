package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;

public record ReservationSeatResponse(
        Long seatId,
        String seatRow,
        Integer seatColumn
) {

    public static ReservationSeatResponse from(
            ReservationSeat reservationSeat
    ) {
        return new ReservationSeatResponse(
                reservationSeat.getSeat().getId(),
                reservationSeat.getSeat().getSeatRow(),
                reservationSeat.getSeat().getSeatColumn()
        );
    }
}