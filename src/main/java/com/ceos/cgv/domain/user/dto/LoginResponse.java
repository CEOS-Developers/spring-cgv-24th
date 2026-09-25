package com.ceos.cgv.domain.user.dto;

public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresInSeconds, long refreshExpiresInSeconds) {
    public static LoginResponse bearer(String accessToken, String refreshToken,
                                       long expiresInSeconds, long refreshExpiresInSeconds) {
        return new LoginResponse(accessToken, refreshToken, "Bearer",
                expiresInSeconds, refreshExpiresInSeconds);
    }
}
