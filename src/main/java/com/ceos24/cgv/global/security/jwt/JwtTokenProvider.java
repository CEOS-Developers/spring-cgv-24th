package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.domain.user.enums.UserRole;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-token-expiration}") long accessTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String createAccessToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userDetails.getUserId()))
                .claim("role", userDetails.getRole().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public AccessTokenInfo parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();
        String roleClaim = claims.get("role", String.class);

        if (!StringUtils.hasText(subject) || !StringUtils.hasText(roleClaim)) {
            throw new IllegalArgumentException("Required JWT claim is missing.");
        }

        Long userId = Long.valueOf(subject);
        UserRole role = UserRole.valueOf(roleClaim);

        return new AccessTokenInfo(userId, role);
    }
}
