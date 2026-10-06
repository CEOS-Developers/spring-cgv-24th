package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.domain.user.enums.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/*
jwt 를 만들고, 검증하고 -> 검증된 토큰에서 정보를 꺼내는 클래스
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationSeconds;
    private final String issuer;
    private final JwtParser jwtParser;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-seconds}")
            long expirationSeconds,
            @Value("${jwt.issuer}") String issuer
    ) {
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "JWT 유효 시간은 0보다 커야 합니다."
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;

        JwtParserBuilder parserBuilder = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer);

        // HS256은 남겨두고 나머지 알고리즘만 제거
        // 검증시의 알고리즘 관련
        var algorithms = parserBuilder.sig();

        for (var algorithm : Jwts.SIG.get().values()) {
            if (!Jwts.SIG.HS256.getId().equals(algorithm.getId())) {
                algorithms.remove(algorithm);
            }
        }


        this.jwtParser = algorithms.and().build();
    }

    // 로그인 인증에 성공한 회원 정보로 호출
    public String createAccessToken(Long userId, UserRole role) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(
                        Date.from(now.plusSeconds(expirationSeconds))
                )
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // 검증에 성공한 경우에만 Claim 반환
    public Claims parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("토큰이 비어 있습니다.");
        }

        // 서명, 허용 알고리즘, 발급자, 만료 여부 검증
        Claims claims = jwtParser.parseSignedClaims(token)
                .getPayload();

        // 만료 시각 자체가 없는 토큰도 거부
        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("만료 시각이 없습니다.");
        }

        String subject = claims.getSubject();
        String role = claims.get("role", String.class);

        if (subject == null || role == null) {
            throw new MalformedJwtException("필수 정보가 없습니다.");
        }

        // 회원 ID 형식과 허용된 역할인지 확인
        try {
            if (Long.parseLong(subject) <= 0) {
                throw new IllegalArgumentException();
            }

            UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException(
                    "사용자 식별 정보 또는 권한이 올바르지 않습니다."
            );
        }

        return claims;
    }
}
