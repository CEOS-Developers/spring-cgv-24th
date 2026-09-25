package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.security.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.InvalidClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    private static final String ISSUER = "cgv-api";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration accessTokenValidity;
    private final Clock clock;
    private final JwtParser parser;

    public JwtProvider(JwtProperties properties, Clock clock) {
        // 256비트 미만이면 WeakKeyException으로 기동이 실패한다. 약한 키로 서비스가 뜨는 것보다 낫다.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.accessTokenValidity = properties.accessTokenValidity();
        this.clock = clock;
        this.parser = Jwts.parser()
                // 헤더의 alg는 토큰을 만든 쪽이 정한다. 서버가 허용할 알고리즘을 따로 못박아 두지 않으면
                // 공격자가 고른 알고리즘으로 검증하게 된다.
                .sig().clear().add(Jwts.SIG.HS256).and()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String createAccessToken(Long userId, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenValidity)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValidity.toSeconds();
    }

    // 실패 유형마다 ErrorCode를 달리 던져 호출자(인증 필터)가 응답을 고를 수 있게 한다.
    // JJWT는 서명을 먼저 검증하고 그다음 exp·iss를 본다. 그래서 EXPIRED_TOKEN은 서명이 맞는 토큰에만 나온다.
    public AuthUser parse(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (SecurityException | UnsupportedJwtException | InvalidClaimException e) {
            // 서명 불일치, 허용하지 않은 알고리즘(alg=none 포함), 다른 발급자
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        } catch (MalformedJwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.MALFORMED_TOKEN);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return toAuthUser(claims);
    }

    // 서명이 맞으면 우리가 발급한 토큰이다. 그런데도 값이 이상하다면 형식 문제로 본다.
    private AuthUser toAuthUser(Claims claims) {
        try {
            Long userId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new AuthUser(userId, role);
        } catch (IllegalArgumentException | NullPointerException | JwtException e) {
            throw new CustomException(ErrorCode.MALFORMED_TOKEN);
        }
    }
}
