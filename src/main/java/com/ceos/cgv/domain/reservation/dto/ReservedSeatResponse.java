package com.ceos.cgv.domain.reservation.dto;

import com.ceos.cgv.domain.reservation.entity.ReservedSeat;

public record ReservedSeatResponse(
        String seatRow,
        Integer seatNumber
) {
    public static ReservedSeatResponse from(ReservedSeat reservedSeat) {
        return new ReservedSeatResponse(reservedSeat.getSeatRow(), reservedSeat.getSeatNumber());
    }
}
