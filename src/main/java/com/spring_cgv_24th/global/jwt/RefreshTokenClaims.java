package com.spring_cgv_24th.global.jwt;

import java.time.Instant;

public record RefreshTokenClaims(
        Long memberId,
        Instant expiresAt
) {
}
