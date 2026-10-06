package com.ceos24.springboot.reservation.dto;

import com.ceos24.springboot.theater.domain.Seat;

public record SeatResponse(
        Long seatId,
        String rowName,
        Integer seatNum
) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getSeatId(),
                seat.getRowName(),
                seat.getSeatNum()
        );
    }
}