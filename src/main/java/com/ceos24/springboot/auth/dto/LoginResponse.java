package com.ceos24.springboot.auth.dto;

public record LoginResponse(
        String accessToken
) {

    public static LoginResponse of(String accessToken) {
        return new LoginResponse(accessToken);
    }
}