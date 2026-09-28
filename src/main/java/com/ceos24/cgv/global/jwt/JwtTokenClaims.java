package com.ceos24.cgv.global.jwt;

public record JwtTokenClaims(
        String username,
        String role
) {
}
