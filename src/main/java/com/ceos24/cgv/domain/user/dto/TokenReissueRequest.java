package com.ceos24.cgv.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

// 쿠키를 쓰지 않으므로 리프레시 토큰은 본문으로만 받는다. 헤더 자리는 액세스 토큰 전용이다.
public record TokenReissueRequest(
        @NotBlank String refreshToken
) {
}
