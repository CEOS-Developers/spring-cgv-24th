package com.ceos24.cgv.domain.screening.dto.response;

import com.ceos24.cgv.domain.cinema.entity.Seat;

public record SeatResponse(
        Long seatId,
        int rowNumber,
        int columnNumber,
        boolean reserved
) {

    public static SeatResponse from(
            Seat seat,
            boolean reserved
    ) {
        return new SeatResponse(
                seat.getId(),
                seat.getRowNumber(),
                seat.getColumnNumber(),
                reserved
        );
    }
}
