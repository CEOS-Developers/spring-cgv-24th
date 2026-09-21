package com.ceos24.cgv.domain.reservation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ReservationCreateRequest(
        @NotNull(message = "상영정보 ID는 필수입니다.")
        @Positive(message = "상영정보 ID는 1 이상이어야 합니다.")
        Long screeningId,

        @NotEmpty(message = "한 개 이상의 좌석을 선택해야 합니다.")
        List<@NotNull(message = "좌석 ID는 null일 수 없습니다.")
                @Positive(message = "좌석 ID는 1 이상이어야 합니다.")
                Long> seatIds
) {
}
