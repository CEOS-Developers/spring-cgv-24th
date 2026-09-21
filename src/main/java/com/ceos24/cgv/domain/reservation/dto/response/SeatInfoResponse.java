package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.theater.entity.Seat;

public record SeatInfoResponse(
        Long seatId,
        Integer rowNum,
        Integer colNum
) {
    public static SeatInfoResponse from(Seat seat) {
        return new SeatInfoResponse(seat.getId(), seat.getRowNum(), seat.getColNum());
    }
}
