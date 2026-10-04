package com.ceos.cgv.domain.reservation.dto;

import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import lombok.Builder;
import java.time.Instant;

@Builder
public record ReservedSeatResponse(
        Long reservedSeatId,
        String seatRow,
        Integer seatNumber,
        Instant canceledAt,
        boolean canceled
) {
    public static ReservedSeatResponse from(ReservedSeat reservedSeat) {
        return new ReservedSeatResponse(reservedSeat.getId(), reservedSeat.getSeatRow(),
                reservedSeat.getSeatNumber(), reservedSeat.getCanceledAt(), reservedSeat.isCanceled());
    }
}
