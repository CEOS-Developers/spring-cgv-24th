package com.ceos24.cgv.domain.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "좌석 예매 요청 DTO")
public record CreateReservationRequest(
        @NotEmpty(message = "예매할 좌석 번호는 1개 이상이어야 합니다.") @Schema(description = "예매할 좌석 번호 목록", example = "[1, 2]")
                List<@NotNull(message = "좌석 번호는 필수입니다.") @Min(value = 1, message = "좌석 번호는 1 이상이어야 합니다.") Long>
                        seatNumbers) {}
