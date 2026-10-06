package com.ceos24.cgv.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SignUpRequest(
        @NotBlank String nickname,
        @NotNull LocalDate birthday,
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String phone
) {
}