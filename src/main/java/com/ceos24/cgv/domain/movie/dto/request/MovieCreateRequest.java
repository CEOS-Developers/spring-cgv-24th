package com.ceos24.cgv.domain.movie.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MovieCreateRequest(
        @NotBlank(message = "영화 이름은 필수입니다.")
        @Size(max = 255, message = "영화 이름은 255자 이하여야 합니다.")
        String name,

        @NotNull(message = "개봉일은 필수입니다.")
        LocalDate releaseDate
) {
}
