package com.ceos24.cgv.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 요청 DTO")
public record SignUpRequest(
        @Schema(description = "이름", example = "홍길동") String name,
        @Schema(description = "이메일", example = "hong@example.com") String email,
        @Schema(description = "로그인 아이디", example = "hong123") String loginId,
        @Schema(description = "비밀번호", example = "password123!") String password,
        @Schema(description = "어드민 가입용 시크릿 토큰") String adminToken) {}
