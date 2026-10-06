package com.ceos24.cgv.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 요청 DTO")
public record LoginRequest(
        @Schema(description = "로그인 아이디", example = "hong123") String loginId,
        @Schema(description = "비밀번호", example = "password123!") String password) {}
