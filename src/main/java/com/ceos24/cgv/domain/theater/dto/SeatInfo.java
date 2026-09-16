package com.ceos24.cgv.domain.theater.dto;

import com.ceos24.cgv.domain.theater.domain.Seat;

public record SeatInfo(Long seatId, Long seatNumber, Boolean isAvailable) {
    public static SeatInfo from(Seat seat) {
        return new SeatInfo(seat.getId(), seat.getSeatNumber(), !seat.getIsReserved());
    }
}
