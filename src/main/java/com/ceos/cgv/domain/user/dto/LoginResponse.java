package com.ceos.cgv.domain.user.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
    public static LoginResponse bearer(String accessToken) {
        return new LoginResponse(accessToken, "Bearer", 1800);
    }
}
