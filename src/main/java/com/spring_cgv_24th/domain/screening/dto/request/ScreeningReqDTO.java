package com.spring_cgv_24th.domain.screening.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public class ScreeningReqDTO {

    private ScreeningReqDTO() {
    }

    public record CreateScreeningDTO(
            @NotNull(message = "영화 ID는 필수입니다.")
            @Positive(message = "영화 ID는 양수이어야 합니다.")
            Long movieId,

            @NotNull(message = "상영관 ID는 필수입니다.")
            @Positive(message = "상영관 ID는 양수이어야 합니다.")
            Long auditoriumId,

            @NotNull(message = "상영 시작 시각은 필수입니다.")
            LocalDateTime startsAt
    ) {
    }
}
