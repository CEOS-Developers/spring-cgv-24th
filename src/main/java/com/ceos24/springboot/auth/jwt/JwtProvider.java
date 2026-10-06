package com.ceos24.springboot.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;


    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.accessTokenExpiration = accessTokenExpiration;
    }

    // Access Token 생성
    public String createAccessToken(Long userId) {

        Date now = new Date();

        Date expiration =
                new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }


    // JWT 검증
    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }


    // 검증된 JWT에서 사용자 ID 추출
    public Long getUserId(String token) {

        Claims claims = parseClaims(token);

        return Long.valueOf(claims.getSubject());
    }

    // JWT 파싱 & 서명/만료 검증
    private Claims parseClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}