package com.ceos24.cgv.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String createAccessToken(String memberId) {
        Date now = new Date();
        return Jwts.builder()
                .issuer("http://localhost:8080")
                .subject(memberId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(String memberId) {
        Date now = new Date();
        return Jwts.builder()
                .issuer("http://localhost:8080")
                .subject(memberId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpirationMs))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String getMemberIdFromTokenAfterValidate(String token) {
        Claims payload = getPayload(token);
        if (!"access".equals(payload.get("type", String.class))) {
            throw new io.jsonwebtoken.JwtException("Invalid token type");
        }
        return payload.getSubject();
    }

    public String validateRefreshToken(String token) {
        Claims payload = getPayload(token);
        if (!"refresh".equals(payload.get("type", String.class))) {
            throw new io.jsonwebtoken.JwtException("Invalid token type");
        }
        return payload.getSubject();
    }

    private Claims getPayload(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer("http://localhost:8080")
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
