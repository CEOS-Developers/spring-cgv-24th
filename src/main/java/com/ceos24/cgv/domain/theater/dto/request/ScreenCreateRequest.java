package com.ceos24.cgv.domain.theater.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScreenCreateRequest(
        @NotNull(message = "상영관 타입은 필수입니다.")
        Long screenTypeId,

        @NotBlank(message = "상영관 이름은 필수입니다.")
        String name
) {
}
