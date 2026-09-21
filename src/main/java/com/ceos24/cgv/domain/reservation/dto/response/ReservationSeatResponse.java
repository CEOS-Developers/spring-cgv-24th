package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;

public record ReservationSeatResponse(
        Long seatId,
        int rowNumber,
        int columnNumber
) {

    public static ReservationSeatResponse from(ReservationSeat reservationSeat) {
        return new ReservationSeatResponse(
                reservationSeat.getSeat().getId(),
                reservationSeat.getSeat().getRowNumber(),
                reservationSeat.getSeat().getColumnNumber()
        );
    }
}
