package com.ceos.cgv.domain.auth.security;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final Clock ISSUED_AT = Clock.fixed(
            Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Test
    void 발급한_토큰에는_내부_회원_ID와_역할만_담기고_30분_뒤_만료된다() {
        JwtService service = new JwtService(SECRET, ISSUED_AT);

        String token = service.issue(42L, UserRole.USER);

        assertThat(service.verify(token)).isEqualTo(new JwtService.VerifiedToken(42L, UserRole.USER));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"42\"")
                .contains("\"role\":\"USER\"")
                .contains("\"token_type\":\"ACCESS\"")
                .contains("\"iss\":\"spring-cgv-24th\"")
                .doesNotContain("email", "password", "passwordHash");
        Number issuedAt = JsonPath.read(payload, "$.iat");
        Number expiresAt = JsonPath.read(payload, "$.exp");
        assertThat(expiresAt.longValue() - issuedAt.longValue()).isEqualTo(1800);

        JwtService afterExpiry = new JwtService(SECRET, Clock.offset(ISSUED_AT, Duration.ofMinutes(31)));
        assertThatThrownBy(() -> afterExpiry.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 용도_표시가_없는_토큰은_일반_API_인증에_사용할_수_없다() {
        JwtService service = new JwtService(SECRET, ISSUED_AT);
        String token = Jwts.builder()
                .issuer("spring-cgv-24th")
                .subject("42")
                .claim("role", "USER")
                .issuedAt(Date.from(ISSUED_AT.instant()))
                .expiration(Date.from(ISSUED_AT.instant().plus(Duration.ofMinutes(30))))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> service.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 변조하거나_다른_키로_서명한_토큰은_검증할_수_없다() {
        JwtService service = new JwtService(SECRET, ISSUED_AT);
        String token = service.issue(42L, UserRole.ADMIN);
        String[] segments = token.split("\\.");
        String tampered = segments[0] + "." + (segments[1].startsWith("A") ? "B" : "A")
                + segments[1].substring(1) + "." + segments[2];
        String otherSecret = Base64.getEncoder().encodeToString(
                "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.verify(tampered)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> new JwtService(otherSecret, ISSUED_AT).verify(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 올바르게_서명됐어도_회원_ID나_역할이_잘못되면_거절한다() {
        JwtService service = new JwtService(SECRET, ISSUED_AT);

        for (String[] claims : new String[][]{{"not-a-number", "USER"}, {"0", "USER"}, {"42", "SUPER_ADMIN"}}) {
            String token = Jwts.builder()
                    .issuer("spring-cgv-24th")
                    .subject(claims[0])
                    .claim("role", claims[1])
                    .issuedAt(Date.from(ISSUED_AT.instant()))
                    .expiration(Date.from(ISSUED_AT.instant().plus(Duration.ofMinutes(30))))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256)
                    .compact();
            assertThatThrownBy(() -> service.verify(token)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void 설정한_초_단위_만료시간으로_토큰이_발급된다() {
        JwtService service = new JwtService(SECRET, ISSUED_AT, 60);

        String token = service.issue(42L, UserRole.USER);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        Number issuedAt = JsonPath.read(payload, "$.iat");
        Number expiresAt = JsonPath.read(payload, "$.exp");

        assertThat(expiresAt.longValue() - issuedAt.longValue()).isEqualTo(60);
        assertThat(service.expiresInSeconds()).isEqualTo(60);
    }

    @Test
    void 리프레시_토큰은_14일간_유효하고_일반_API에_사용할_수_없다() {
        JwtService service = new JwtService(SECRET, ISSUED_AT);

        String first = service.issueRefresh(42L);
        String second = service.issueRefresh(42L);
        String payload = new String(Base64.getUrlDecoder().decode(first.split("\\.")[1]),
                StandardCharsets.UTF_8);
        Number issuedAt = JsonPath.read(payload, "$.iat");
        Number expiresAt = JsonPath.read(payload, "$.exp");

        assertThat(first).isNotEqualTo(second);
        assertThat(payload).contains("\"token_type\":\"REFRESH\"")
                .contains("\"sub\":\"42\"")
                .doesNotContain("role", "password");
        assertThat(JsonPath.<String>read(payload, "$.jti")).isNotBlank();
        assertThat(expiresAt.longValue() - issuedAt.longValue()).isEqualTo(14 * 24 * 60 * 60);
        assertThat(service.verifyRefresh(first)).isEqualTo(42L);
        assertThatThrownBy(() -> service.verify(first)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.verifyRefresh(service.issue(42L, UserRole.USER)))
                .isInstanceOf(JwtException.class);

        JwtService afterExpiry = new JwtService(SECRET,
                Clock.offset(ISSUED_AT, Duration.ofDays(15)));
        assertThatThrownBy(() -> afterExpiry.verifyRefresh(first)).isInstanceOf(JwtException.class);
    }
}
