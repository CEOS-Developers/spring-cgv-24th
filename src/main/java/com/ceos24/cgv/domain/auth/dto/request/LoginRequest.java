package com.ceos24.cgv.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청 DTO")
public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다.") @Schema(description = "로그인 아이디", example = "hong123") String loginId,
        @NotBlank(message = "비밀번호는 필수입니다.") @Schema(description = "비밀번호", example = "password123!") String password) {}
