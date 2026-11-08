package com.ceos.cgv.domain.reservation.dto;

import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
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
        // 좌석 취소 시각이 없던 과거 전체 취소도 응답에는 취소로 표시한다.
        boolean canceled = reservedSeat.isCanceled()
                || reservedSeat.getReservation().getStatus() == ReservationStatus.CANCELED;
        return new ReservedSeatResponse(reservedSeat.getId(), reservedSeat.getSeatRow(),
                reservedSeat.getSeatNumber(), reservedSeat.getCanceledAt(), canceled);
    }
}
