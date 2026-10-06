package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.security.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

    // 만료만 따로 알려 클라이언트가 재로그인을 유도할 수 있게 하고, 나머지는 원인을 구분하지 않는다.
    // 변조·형식 오류를 세분해 알려주면 공격자에게 어느 단계까지 통과했는지 단서가 된다.
    // JJWT는 서명을 먼저 검증하고 그다음 exp·iss를 본다. 그래서 TOKEN_EXPIRED는 서명이 맞는 토큰에만 나온다.
    public AuthUser parse(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치, 허용하지 않은 알고리즘(alg=none 포함), 다른 발급자, 형식 오류, 빈 값
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
        return toAuthUser(claims);
    }

    private AuthUser toAuthUser(Claims claims) {
        try {
            Long userId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new AuthUser(userId, role);
        } catch (IllegalArgumentException | NullPointerException | JwtException e) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
    }
}
