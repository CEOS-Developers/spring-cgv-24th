package com.ceos24.cgv.global.jwt;

import com.ceos24.cgv.global.exception.JwtTokenExpiredException;
import com.ceos24.cgv.global.exception.JwtTokenInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String ISSUER = "cgv-api";
    private static final String SECRET = Base64.getEncoder()
            .encodeToString(
                    "01234567890123456789012345678901"
                            .getBytes(StandardCharsets.UTF_8)
            );

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = providerWithAccessExpiration(Duration.ofHours(1));
    }

    @Test
    void Access_Token을_생성하고_검증한다() {
        String token = jwtTokenProvider.createAccessToken(
                "test-user",
                "ROLE_USER"
        );

        JwtTokenClaims claims = jwtTokenProvider.parseAccessToken(token);

        assertEquals("test-user", claims.username());
        assertEquals("ROLE_USER", claims.role());
    }

    @Test
    void Refresh_Token을_생성하고_검증한다() {
        String token = jwtTokenProvider.createRefreshToken(
                "test-user",
                "ROLE_USER"
        );

        JwtTokenClaims claims = jwtTokenProvider.parseRefreshToken(token);

        assertEquals("test-user", claims.username());
        assertEquals("ROLE_USER", claims.role());
    }

    @Test
    void Refresh_Token은_Access_Token으로_사용할_수_없다() {
        String refreshToken = jwtTokenProvider.createRefreshToken(
                "test-user",
                "ROLE_USER"
        );

        assertThrows(
                JwtTokenInvalidException.class,
                () -> jwtTokenProvider.parseAccessToken(refreshToken)
        );
    }

    @Test
    void 변조된_Token은_거부한다() {
        String token = jwtTokenProvider.createAccessToken(
                "test-user",
                "ROLE_USER"
        );
        char replacement = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';
        String tamperedToken = token.substring(0, token.length() - 1) + replacement;

        assertThrows(
                JwtTokenInvalidException.class,
                () -> jwtTokenProvider.parseAccessToken(tamperedToken)
        );
    }

    @Test
    void 만료된_Token은_만료_예외를_발생시킨다() {
        JwtTokenProvider expiredTokenProvider =
                providerWithAccessExpiration(Duration.ofSeconds(-1));
        String expiredToken = expiredTokenProvider.createAccessToken(
                "test-user",
                "ROLE_USER"
        );

        assertThrows(
                JwtTokenExpiredException.class,
                () -> expiredTokenProvider.parseAccessToken(expiredToken)
        );
    }

    private JwtTokenProvider providerWithAccessExpiration(Duration expiration) {
        JwtProperties properties = new JwtProperties(
                SECRET,
                expiration,
                Duration.ofDays(14),
                ISSUER
        );

        return new JwtTokenProvider(properties);
    }
}
