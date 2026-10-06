package com.cgvclone.cgv.domain.auth;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final String issuer;
    private final String audience;
    private final JwtParser parser;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") Duration expiration,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience) {
        try {
            signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        } catch (IllegalArgumentException | io.jsonwebtoken.security.WeakKeyException exception) {
            throw new IllegalArgumentException("JWT_SECRET에는 32바이트 이상의 키를 Base64로 인코딩한 값이 필요합니다.");
        }
        if (expiration == null || expiration.getSeconds() < 1 || issuer == null || issuer.isBlank() || audience == null
                || audience.isBlank()) {
            throw new IllegalArgumentException("JWT 만료 시간은 1초 이상이며 발급자와 대상은 필수입니다.");
        }
        this.expirationSeconds = expiration.getSeconds();
        this.issuer = issuer;
        this.audience = audience;
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build();
    }

    public String createAccessToken(CustomUserDetails userDetails) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Jwts.builder()
                .subject(userDetails.getUserId().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .claim("authorities", authorities)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public VerifiedAccessToken verifyAccessToken(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            if (issuedAt == null || expiration == null || !expiration.after(issuedAt)
                    || issuedAt.toInstant().isAfter(Instant.now())) {
                throw new GlobalException(ErrorCode.TOKEN_INVALID);
            }
            if (!expiration.toInstant().isAfter(Instant.now())) {
                throw new GlobalException(ErrorCode.TOKEN_EXPIRED);
            }
            long userId = Long.parseLong(claims.getSubject());
            Object rawAuthorities = claims.get("authorities");
            if (userId <= 0 || !(rawAuthorities instanceof List<?> authorities)
                    || authorities.stream().anyMatch(value -> !(value instanceof String authority)
                    || authority.isBlank())) {
                throw new GlobalException(ErrorCode.TOKEN_INVALID);
            }
            return new VerifiedAccessToken(userId, authorities.stream().map(String.class::cast).toList());
        } catch (ExpiredJwtException exception) {
            throw new GlobalException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new GlobalException(ErrorCode.TOKEN_INVALID);
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
