package com.ceos24.cgv.domain.cinema.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditoriumCreateRequest(
        @NotBlank(message = "상영관 이름은 필수입니다.")
        @Size(max = 50, message = "상영관 이름은 50자 이하여야 합니다.")
        String name,

        @NotNull(message = "상영관 종류 ID는 필수입니다.")
        Long auditoriumTypeId

        // cinemaId는 url로 받음
) {
}
