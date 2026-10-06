package com.ceos24.cgv.domain.user.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {
}
