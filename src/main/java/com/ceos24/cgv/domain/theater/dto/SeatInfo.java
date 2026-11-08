package com.ceos24.cgv.domain.theater.dto;

import com.ceos24.cgv.domain.theater.entity.Seat;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좌석 정보 DTO")
public record SeatInfo(
        @Schema(description = "좌석 ID", example = "1") Long seatId,
        @Schema(description = "좌석 번호", example = "1") Long seatNumber,
        @Schema(description = "예매 가능 여부", example = "true") Boolean isAvailable) {
    public static SeatInfo from(Seat seat) {
        return new SeatInfo(seat.getId(), seat.getSeatNumber(), !seat.getIsReserved());
    }
}
