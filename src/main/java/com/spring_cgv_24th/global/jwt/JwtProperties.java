package com.spring_cgv_24th.global.jwt;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {

    @AssertTrue(message = "Access Token 만료 시간은 0보다 커야 합니다.")
    public boolean isAccessTokenExpirationValid() {
        return accessTokenExpiration != null && accessTokenExpiration.isPositive();
    }

    @AssertTrue(message = "Refresh Token 만료 시간은 0보다 커야 합니다.")
    public boolean isRefreshTokenExpirationValid() {
        return refreshTokenExpiration != null && refreshTokenExpiration.isPositive();
    }
}
