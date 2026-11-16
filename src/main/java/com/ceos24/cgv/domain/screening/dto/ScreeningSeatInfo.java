package com.ceos24.cgv.domain.screening.dto;

import com.ceos24.cgv.domain.theater.entity.Seat;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좌석 정보 DTO")
public record ScreeningSeatInfo(
        @Schema(description = "좌석 ID", example = "1") Long seatId,
        @Schema(description = "좌석 번호", example = "1") Long seatNumber,
        @Schema(description = "예매 가능 여부", example = "true") Boolean isAvailable,
        @Schema(description = "좌석 상태 (AVAILABLE: 예매 가능, HOLD: 결제 대기 중, RESERVED: 예매 완료)", example = "AVAILABLE")
                SeatStatus status) {
    public static ScreeningSeatInfo of(Seat seat, SeatStatus status) {
        return new ScreeningSeatInfo(seat.getId(), seat.getSeatNumber(), status == SeatStatus.AVAILABLE, status);
    }
}
