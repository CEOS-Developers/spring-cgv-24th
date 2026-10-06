package com.spring_cgv_24th.global.jwt;

import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final MacAlgorithm SIGNATURE_ALGORITHM = Jwts.SIG.HS256;
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Clock clock;
    private final JwtParser accessTokenParser;
    private final JwtParser refreshTokenParser;

    public JwtProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = createSigningKey(properties.secret());
        this.accessTokenParser = createParser(ACCESS_TOKEN_TYPE);
        this.refreshTokenParser = createParser(REFRESH_TOKEN_TYPE);
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());

        return Jwts.builder()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(memberId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(ROLE_CLAIM, role.name())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .signWith(signingKey, SIGNATURE_ALGORITHM)
                .compact();
    }

    public String createRefreshToken(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("회원 ID가 올바르지 않습니다.");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.refreshTokenExpiration());

        // 같은 회원에게 같은 초에 발급해도 서로 다른 토큰이 되도록 고유 ID를 넣는다.
        return Jwts.builder()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(memberId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .signWith(signingKey, SIGNATURE_ALGORITHM)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = accessTokenParser.parseSignedClaims(token).getPayload();
            return toAccessTokenClaims(claims);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
    }

    public RefreshTokenClaims parseRefreshToken(String token) {
        try {
            Claims claims = refreshTokenParser.parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || claims.getIssuedAt() == null
                    || claims.getId() == null || claims.getId().isBlank()) {
                throw new IllegalArgumentException("필수 Refresh Token Claim이 없습니다.");
            }
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();
            if (issuedAt.isAfter(clock.instant()) || !expiresAt.isAfter(issuedAt)) {
                throw new IllegalArgumentException("Refresh Token 시간 Claim이 올바르지 않습니다.");
            }
            // JWT 만료 시각과 현재 시각이 정확히 같아도 이미 만료된 것으로 처리한다.
            if (!expiresAt.isAfter(clock.instant())) {
                throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
            }
            Long memberId = Long.valueOf(claims.getSubject());
            if (memberId <= 0) {
                throw new IllegalArgumentException("회원 ID가 올바르지 않습니다.");
            }
            return new RefreshTokenClaims(memberId, expiresAt);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private JwtParser createParser(String tokenType) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .require(TOKEN_TYPE_CLAIM, tokenType)
                .sig()
                    .clear()
                    .add(SIGNATURE_ALGORITHM)
                    .and()
                .build();
    }

    private AccessTokenClaims toAccessTokenClaims(Claims claims) {
        if (claims.getExpiration() == null || claims.getIssuedAt() == null) {
            throw new IllegalArgumentException("필수 시간 Claim이 없습니다.");
        }

        // Refresh Token과 동일하게 현재 시각이 만료 시각에 도달한 경우부터 거부한다.
        if (!claims.getExpiration().toInstant().isAfter(clock.instant())) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        }

        String subject = claims.getSubject();
        String roleClaim = claims.get(ROLE_CLAIM, String.class);
        if (subject == null || roleClaim == null) {
            throw new IllegalArgumentException("필수 사용자 Claim이 없습니다.");
        }

        Long memberId = Long.valueOf(subject);
        if (memberId <= 0) {
            throw new IllegalArgumentException("회원 ID가 올바르지 않습니다.");
        }

        MemberRole role = MemberRole.valueOf(roleClaim);
        return new AccessTokenClaims(memberId, role);
    }

    private SecretKey createSigningKey(String secret) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (DecodingException | WeakKeyException e) {
            throw new IllegalStateException(
                    "JWT_SECRET은 Base64로 인코딩된 256비트 이상의 키여야 합니다.", e);
        }
    }
}
