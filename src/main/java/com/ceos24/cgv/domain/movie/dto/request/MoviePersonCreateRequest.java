package com.ceos24.cgv.domain.movie.dto.request;

import com.ceos24.cgv.domain.movie.enums.PersonRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MoviePersonCreateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,

        String personProfileImageUrl,

        @NotNull(message = "역할(감독/배우)은 필수입니다.")
        PersonRole role
) {
}
