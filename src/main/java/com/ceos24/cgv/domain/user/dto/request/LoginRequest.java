package com.ceos24.cgv.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(description = "아이디", example = "testuser")
        @NotBlank
        String username,

        @Schema(description = "비밀번호", example = "password1234")
        @NotBlank
        String password
) {
}
