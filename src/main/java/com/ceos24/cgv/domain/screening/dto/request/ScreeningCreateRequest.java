package com.ceos24.cgv.domain.screening.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

public record ScreeningCreateRequest(
        @NotNull(message = "영화 ID는 필수입니다.")
        Long movieId,

        @NotNull(message = "상영관 ID는 필수입니다.")
        Long auditoriumId,

        @NotNull(message = "상영 시작 시각은 필수입니다.")
        LocalDateTime startsAt,

        @NotNull(message = "상영 종료 시각은 필수입니다.")
        LocalDateTime endsAt,

        @NotNull(message = "상영 가격은 필수입니다.")
        @PositiveOrZero(message = "상영 가격은 0 이상이어야 합니다.")
        Integer price
) {
}
