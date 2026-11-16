package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "예매 응답 DTO")
public record ReservationResponse(
        @Schema(description = "예매 ID", example = "1") Long reservationId,
        @Schema(description = "상영 일정 ID", example = "1") Long screeningId,
        @Schema(description = "예매 상태", example = "HOLD") ReservationStatus status,
        @Schema(description = "총 결제 금액", example = "28000") Long totalPrice,
        @Schema(description = "예매된 좌석 번호 목록", example = "[1, 2]") List<Long> seatNumbers,
        @Schema(description = "임시 점유 만료 시각") LocalDateTime expiresAt,
        @Schema(description = "예매 일시") LocalDateTime createdAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getScreening().getId(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getSeatNumbers(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt());
    }
}
