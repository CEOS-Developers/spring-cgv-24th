package com.ceos24.cgv.global.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret Base64로 인코딩한 HS256 서명키. 디코딩 결과가 256비트 이상이어야 한다.
 */
@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenValidity
) {
}
