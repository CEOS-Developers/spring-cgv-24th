package com.ceos24.cgv.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원가입 요청 DTO")
public record SignUpRequest(
        @NotBlank(message = "이름은 필수입니다.") @Schema(description = "이름", example = "홍길동") String name,
        @NotBlank(message = "이메일은 필수입니다.")
                @Email(message = "올바른 이메일 형식이 아닙니다.")
                @Schema(description = "이메일", example = "hong@example.com")
                String email,
        @NotBlank(message = "아이디는 필수입니다.") @Schema(description = "로그인 아이디", example = "hong123") String loginId,
        @NotBlank(message = "비밀번호는 필수입니다.") @Schema(description = "비밀번호", example = "password123!") String password,
        @Schema(description = "어드민 가입용 시크릿 토큰") String adminToken) {}
