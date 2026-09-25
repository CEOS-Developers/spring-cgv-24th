package com.ceos.cgv.domain.user.security;

import com.ceos.cgv.domain.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private static final String ISSUER = "spring-cgv-24th";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 1800;
    private static final long DEFAULT_REFRESH_EXPIRES_IN_SECONDS = 14L * 24 * 60 * 60;

    private final SecretKey signingKey;
    private final Clock clock;
    private final long expiresInSeconds;
    private final long refreshExpiresInSeconds;

    @Autowired
    public JwtService(@Value("${cgv.jwt.secret-base64}") String secretBase64,
                      @Value("${cgv.jwt.access-token-seconds}") long expiresInSeconds,
                      @Value("${cgv.jwt.refresh-token-seconds}") long refreshExpiresInSeconds) {
        this(secretBase64, Clock.systemUTC(), expiresInSeconds, refreshExpiresInSeconds);
    }

    JwtService(String secretBase64, Clock clock) {
        this(secretBase64, clock, DEFAULT_EXPIRES_IN_SECONDS, DEFAULT_REFRESH_EXPIRES_IN_SECONDS);
    }

    JwtService(String secretBase64, Clock clock, long expiresInSeconds) {
        this(secretBase64, clock, expiresInSeconds, DEFAULT_REFRESH_EXPIRES_IN_SECONDS);
    }

    JwtService(String secretBase64, Clock clock, long expiresInSeconds, long refreshExpiresInSeconds) {
        byte[] keyBytes = Decoders.BASE64.decode(secretBase64);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT signing key must be at least 32 bytes");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("JWT expiry must be positive");
        }
        if (refreshExpiresInSeconds <= 0) {
            throw new IllegalArgumentException("Refresh JWT expiry must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.clock = clock;
        this.expiresInSeconds = expiresInSeconds;
        this.refreshExpiresInSeconds = refreshExpiresInSeconds;
    }

    public String issue(Long userId, UserRole role) {
        if (userId == null || userId <= 0 || role == null) {
            throw new IllegalArgumentException("Invalid JWT subject or role");
        }
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim("role", role.name())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expiresInSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public long expiresInSeconds() {
        return expiresInSeconds;
    }

    public long refreshExpiresInSeconds() {
        return refreshExpiresInSeconds;
    }

    public String issueRefresh(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid refresh JWT subject");
        }
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(refreshExpiresInSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Long verifyRefresh(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())
                || !REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                || claims.getIssuedAt() == null || claims.getExpiration() == null
                || claims.getSubject() == null || claims.getId() == null || claims.getId().isBlank()) {
            throw new MalformedJwtException("Invalid refresh token claims");
        }
        try {
            Long userId = Long.valueOf(claims.getSubject());
            if (userId <= 0) {
                throw new MalformedJwtException("Invalid refresh token subject");
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw new MalformedJwtException("Invalid refresh token claims", exception);
        }
    }

    public VerifiedToken verify(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())
                || !ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                || claims.getIssuedAt() == null || claims.getExpiration() == null
                || claims.getSubject() == null || claims.get("role", String.class) == null) {
            throw new MalformedJwtException("Invalid access token claims");
        }
        try {
            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get("role", String.class));
            if (userId <= 0) {
                throw new MalformedJwtException("Invalid access token subject");
            }
            return new VerifiedToken(userId, role);
        } catch (IllegalArgumentException exception) {
            throw new MalformedJwtException("Invalid access token claims", exception);
        }
    }

    public record VerifiedToken(Long userId, UserRole role) {
    }
}
