package com.ceos24.cgv.domain.movie.dto.request;

import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MovieImageCreateRequest(
        @NotBlank(message = "이미지 URL은 필수입니다.")
        String movieImageUrl,

        @NotNull(message = "이미지 타입은 필수입니다.")
        MovieImageType type
) {
}
