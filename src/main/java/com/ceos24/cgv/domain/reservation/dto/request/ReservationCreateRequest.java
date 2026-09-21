package com.ceos24.cgv.domain.reservation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ReservationCreateRequest(
        @NotNull(message = "상영 스케줄은 필수입니다.")
        Long scheduleId,

        @NotEmpty(message = "좌석은 최소 1개 이상 선택해야 합니다.")
        List<@NotNull(message = "좌석 id는 null일 수 없습니다.") @Positive(message = "좌석 id는 양수여야 합니다.") Long> seatIds
) {
}

