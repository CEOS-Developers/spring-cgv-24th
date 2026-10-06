package com.ceos24.cgv.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

// 가입 형식 규칙을 여기에 적용하지 않는다. 규칙 위반(400)과 인증 실패(401)가 갈리면
// 응답만 보고 계정 존재 여부나 비밀번호 규칙을 추측할 단서가 생긴다.
public record LoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
}
