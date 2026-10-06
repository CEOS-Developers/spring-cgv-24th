package com.spring_cgv_24th.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtRefreshTokenTest {

    private static final String ISSUER = "spring-cgv-24th";
    private static final String AUDIENCE = "spring-cgv-api";
    private static final Instant NOW = Instant.parse("2026-09-29T06:00:00Z");
    private static final Duration REFRESH_EXPIRATION = Duration.ofDays(7);

    private SecretKey signingKey;
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        // 충분한 길이의 같은 키로 HS512도 서명하여 알고리즘 제한 자체를 테스트한다.
        signingKey = Jwts.SIG.HS512.key().build();
        JwtProperties properties = new JwtProperties(
                Encoders.BASE64.encode(signingKey.getEncoded()), ISSUER, AUDIENCE,
                Duration.ofMinutes(15), REFRESH_EXPIRATION);
        jwtProvider = new JwtProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // Refresh JWT에는 회원, 발급자·대상, 발급·만료 시각, 고유 ID와 용도를 담고 역할은 넣지 않는다.
    @Test
    void issuesRefreshJwtWithRequiredClaimsAndNoRole() {
        String token = jwtProvider.createRefreshToken(1L);
        Claims claims = readClaims(token);

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly(AUDIENCE);
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(REFRESH_EXPIRATION));
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("tokenType", String.class)).isEqualTo("REFRESH");
        assertThat(claims).doesNotContainKey("role");
    }

    // 같은 회원에게 같은 시각에 발급해도 jti 덕분에 서로 다른 토큰이 생성된다.
    @Test
    void issuesUniqueTokensEvenAtSameInstant() {
        Set<String> tokens = new HashSet<>();
        Set<String> tokenIds = new HashSet<>();
        for (int index = 0; index < 100; index++) {
            String token = jwtProvider.createRefreshToken(1L);
            tokens.add(token);
            tokenIds.add(readClaims(token).getId());
        }

        assertThat(tokens).hasSize(100);
        assertThat(tokenIds).hasSize(100);
    }

    // 검증 결과에는 재발급 시 회원을 조회할 ID와 잠금 대기 후 재검사할 만료 시각을 반환한다.
    @Test
    void parsesVerifiedMemberAndExpirationWithoutExtendingLifetime() {
        String token = jwtProvider.createRefreshToken(7L);

        RefreshTokenClaims first = jwtProvider.parseRefreshToken(token);
        RefreshTokenClaims second = jwtProvider.parseRefreshToken(token);

        assertThat(first.memberId()).isEqualTo(7L);
        assertThat(first.expiresAt()).isEqualTo(NOW.plus(REFRESH_EXPIRATION));
        assertThat(second).isEqualTo(first);
    }

    // Refresh Token을 일반 API용 Access Token 검증에 넘기면 거부한다.
    @Test
    void rejectsRefreshTokenAsAccessToken() {
        String token = jwtProvider.createRefreshToken(1L);
        CustomException error = assertThrows(CustomException.class, () -> jwtProvider.parseAccessToken(token));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    // role이 추가된 Refresh JWT도 ACCESS 용도가 아니므로 일반 API 인증에 사용할 수 없다.
    @Test
    void rejectsRefreshTokenWithRoleAsAccessToken() {
        String token = refreshBuilder().claim("role", "ADMIN")
                .signWith(signingKey, Jwts.SIG.HS256).compact();

        CustomException error = assertThrows(CustomException.class, () -> jwtProvider.parseAccessToken(token));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    // Access Token도 Refresh Token 검증을 통과할 수 없다.
    @Test
    void rejectsAccessTokenAsRefreshToken() {
        assertRefreshError(jwtProvider.createAccessToken(1L, MemberRole.USER), ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 다른 필수 Claim이 모두 있어도 REFRESH 이외의 용도로 발급된 토큰은 거부한다.
    @ParameterizedTest
    @ValueSource(strings = {"ACCESS", "UNKNOWN"})
    void rejectsDifferentTokenTypeWithAllRequiredClaims(String tokenType) {
        String token = refreshBuilder().claim("tokenType", tokenType)
                .signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 서명이 정상이어도 이미 만료된 Refresh Token은 만료 오류로 구분한다.
    @Test
    void rejectsExpiredRefreshToken() {
        String token = refreshBuilder()
                .issuedAt(Date.from(NOW.minusSeconds(60)))
                .expiration(Date.from(NOW.minusSeconds(1)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    // 만료 시각에 정확히 도달한 토큰도 사용할 수 없다.
    @Test
    void rejectsTokenAtExactExpiration() {
        String token = refreshBuilder()
                .issuedAt(Date.from(NOW.minusSeconds(60)))
                .expiration(Date.from(NOW))
                .signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    // JWT 서명 부분을 변조하면 저장된 해시 검사와 무관하게 JWT 검증 단계에서 거부한다.
    @Test
    void rejectsTamperedToken() {
        String[] parts = jwtProvider.createRefreshToken(1L).split("\\.");
        parts[2] = (parts[2].charAt(0) == 'a' ? 'b' : 'a') + parts[2].substring(1);

        assertRefreshError(String.join(".", parts), ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 서버와 다른 키로 서명한 Refresh Token은 거부한다.
    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String token = refreshBuilder().signWith(Jwts.SIG.HS256.key().build(), Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 올바른 키라도 다른 발급자의 토큰은 허용하지 않는다.
    @Test
    void rejectsDifferentIssuer() {
        String token = refreshBuilder().issuer("other-issuer").signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 다른 서비스 대상으로 발급된 Refresh Token도 허용하지 않는다.
    @Test
    void rejectsDifferentAudience() {
        String token = refreshBuilder().claim("aud", Set.of("other-api"))
                .signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 같은 키로 서명이 검증될 수 있더라도 지정한 HS256 이외의 알고리즘은 거부한다.
    @Test
    void rejectsUnapprovedSignatureAlgorithm() {
        String token = refreshBuilder().signWith(signingKey, Jwts.SIG.HS512).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 필수 Claim을 하나라도 제거한 Refresh Token은 사용할 수 없다.
    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "iat", "exp", "jti", "tokenType"})
    void rejectsMissingRequiredClaim(String claimName) {
        String token = refreshBuilder().claim(claimName, null).signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 회원 ID는 양의 정수여야 한다.
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5"})
    void rejectsInvalidMemberId(String subject) {
        String token = refreshBuilder().subject(subject).signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 비어 있는 고유 ID도 정상 발급 토큰으로 취급하지 않는다.
    @Test
    void rejectsBlankTokenId() {
        String token = refreshBuilder().id(" ").signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 서버 시각보다 미래에 발급되었다는 토큰은 잘못된 시간 Claim으로 거부한다.
    @Test
    void rejectsFutureIssuedAt() {
        String token = refreshBuilder().issuedAt(Date.from(NOW.plusSeconds(1)))
                .signWith(signingKey, Jwts.SIG.HS256).compact();

        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 누락되거나 JWT 형식이 아닌 문자열은 유효하지 않은 Refresh Token으로 처리한다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t", "not-a-jwt"})
    void rejectsMissingOrMalformedToken(String token) {
        assertRefreshError(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 발급 함수에도 존재 가능한 회원 ID만 전달해야 한다.
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void refusesIssueForInvalidMemberId(Long memberId) {
        assertThrows(IllegalArgumentException.class, () -> jwtProvider.createRefreshToken(memberId));
    }

    private JwtBuilder refreshBuilder() {
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject("1")
                .id("test-token-id")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(REFRESH_EXPIRATION)))
                .claim("tokenType", "REFRESH");
    }

    private Claims readClaims(String token) {
        return Jwts.parser().verifyWith(signingKey)
                .clock(() -> Date.from(NOW)).build().parseSignedClaims(token).getPayload();
    }

    private void assertRefreshError(String token, ErrorCode expected) {
        CustomException error = assertThrows(CustomException.class, () -> jwtProvider.parseRefreshToken(token));
        assertThat(error.getErrorCode()).isEqualTo(expected);
    }
}
