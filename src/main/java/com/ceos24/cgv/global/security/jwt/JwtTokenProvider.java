package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.global.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String createAccessToken(CustomUserDetails userDetails) {

        Date now = new Date();
        Date expiration = new Date(
                now.getTime() + accessTokenExpiration
        );

        return Jwts.builder()
                .subject(String.valueOf(userDetails.getUserId()))
                .claim("role", userDetails.getRole().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
