package com.ceos24.cgv.global.jwt;

import com.ceos24.cgv.global.exception.JwtTokenExpiredException;
import com.ceos24.cgv.global.exception.JwtTokenInvalidException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "type";

    private final JwtProperties properties;
    private final SecretKey secretKey;
    private final JwtParser jwtParser;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;

        try {
            byte[] keyBytes = Decoders.BASE64.decode(properties.secret());

            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT 비밀키 설정이 올바르지 않습니다.",
                    exception
            );
        }

        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.issuer())
                .build();
    }

    // Access Token 발급
    public String createAccessToken(
            String username,
            String role
    ) {
        return createToken(
                username,
                role,
                TokenType.ACCESS,
                properties.accessTokenExpiration()
        );
    }

    // Refresh Token 발급
    public String createRefreshToken(
            String username,
            String role
    ) {
        return createToken(
                username,
                role,
                TokenType.REFRESH,
                properties.refreshTokenExpiration()
        );
    }

    // 토큰 발급 메서드
    private String createToken(
            String username,
            String role,
            TokenType tokenType,
            Duration expiration
    ) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiration);

        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(username)
                .claim(ROLE_CLAIM, role)
                .claim(TYPE_CLAIM, tokenType.value())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // Access Token 검증 및 정보 추출
    public JwtTokenClaims parseAccessToken(String token) {
        Claims claims = parseToken(
                token,
                TokenType.ACCESS
        );

        return toTokenClaims(claims);
    }

    // Refresh Token 검증 및 정보 추출
    public JwtTokenClaims parseRefreshToken(String token) {
        Claims claims = parseToken(
                token,
                TokenType.REFRESH
        );

        return toTokenClaims(claims);
    }

    // 토큰 검증 및 정보 추출
    private Claims parseToken(
            String token,
            TokenType expectedType
    ) {
        if (!StringUtils.hasText(token)) {
            throw new JwtTokenInvalidException();
        }

        try {
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();

            String actualType =
                    claims.get(TYPE_CLAIM, String.class);

            if (!expectedType.value().equals(actualType)) {
                throw new JwtTokenInvalidException();
            }

            validateRequiredClaims(claims);

            return claims;

        } catch (ExpiredJwtException exception) {
            throw new JwtTokenExpiredException(exception);

        } catch (JwtTokenInvalidException exception) {
            throw exception;

        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtTokenInvalidException(exception);
        }
    }

    private void validateRequiredClaims(Claims claims) {
        String username = claims.getSubject();
        String role = claims.get(ROLE_CLAIM, String.class);

        if (!StringUtils.hasText(username)
                || !StringUtils.hasText(role)) {
            throw new JwtTokenInvalidException();
        }
    }

    private JwtTokenClaims toTokenClaims(Claims claims) {
        return new JwtTokenClaims(
                claims.getSubject(),
                claims.get(ROLE_CLAIM, String.class)
        );
    }
}
