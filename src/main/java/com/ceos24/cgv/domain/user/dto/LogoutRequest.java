package com.ceos24.cgv.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

// 액세스 토큰이 이미 만료된 기기도 로그아웃할 수 있어야 하므로 리프레시 토큰 자체를 자격 증명으로 받는다.
public record LogoutRequest(
        @NotBlank String refreshToken
) {
}
