package com.spring_cgv_24th.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.auth.token.RefreshTokenHasher;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import com.spring_cgv_24th.global.jwt.JwtProperties;
import com.spring_cgv_24th.global.jwt.JwtProvider;
import com.spring_cgv_24th.global.jwt.RefreshTokenClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-29T06:00:00Z");
    private static final Duration REFRESH_EXPIRATION = Duration.ofDays(7);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock private MemberRepository memberRepository;

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();
    private JwtProperties properties;
    private JwtProvider jwtProvider;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(
                Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()),
                "spring-cgv-24th", "spring-cgv-api", Duration.ofMinutes(15), REFRESH_EXPIRATION);
        jwtProvider = new JwtProvider(properties, FIXED_CLOCK);
        refreshTokenService = new RefreshTokenService(memberRepository, jwtProvider, hasher, FIXED_CLOCK);
    }

    // 응답용 JWT 원문을 반환하고 잠금 조회한 회원에는 SHA-256 해시만 기록한다.
    @Test
    void issuesRefreshJwtButStoresOnlyHashOnMember() {
        Member member = createMember();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        String issuedToken = refreshTokenService.issue(MEMBER_ID);
        RefreshTokenClaims claims = jwtProvider.parseRefreshToken(issuedToken);

        assertThat(issuedToken.split("\\.")).hasSize(3);
        assertThat(member.getRefreshTokenHash()).isEqualTo(hasher.hash(issuedToken)).isNotEqualTo(issuedToken);
        assertThat(claims.memberId()).isEqualTo(MEMBER_ID);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(REFRESH_EXPIRATION));
        verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        // 관리 상태의 Member 변경은 트랜잭션의 변경 감지로 반영한다.
        verify(memberRepository, never()).save(any(Member.class));
    }

    // 같은 시각의 새 로그인 발급도 이전 해시를 대체하므로 최신 토큰만 사용할 수 있다.
    @Test
    void replacesPreviousTokenForSameMember() {
        Member member = createMember();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        String firstToken = refreshTokenService.issue(MEMBER_ID);
        String secondToken = refreshTokenService.issue(MEMBER_ID);

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(member.getRefreshTokenHash()).isEqualTo(hasher.hash(secondToken));
        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(firstToken), ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(refreshTokenService.findMemberByValidToken(secondToken)).isSameAs(member);
    }

    // 로그아웃으로 해시를 삭제했어도 다시 로그인하면 새 토큰 해시를 저장한다.
    @Test
    void issuesNewTokenAfterRevocation() {
        String previousToken = jwtProvider.createRefreshToken(MEMBER_ID);
        Member member = createMemberWithToken(previousToken);
        member.revokeRefreshToken();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        String issuedToken = refreshTokenService.issue(MEMBER_ID);

        assertThat(member.getRefreshTokenHash()).isEqualTo(hasher.hash(issuedToken));
        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(previousToken), ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(refreshTokenService.findMemberByValidToken(issuedToken)).isSameAs(member);
    }

    // 존재하지 않는 회원에게는 토큰을 발급하지 않는다.
    @Test
    void rejectsIssueForUnknownMember() {
        CustomException error = assertThrows(CustomException.class, () -> refreshTokenService.issue(MEMBER_ID));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // 검증된 sub로 회원을 잠금 조회하고 해시나 JWT의 기존 만료 시각을 바꾸지 않는다.
    @Test
    void findsMemberWithoutChangingHashOrExpiration() {
        String token = jwtProvider.createRefreshToken(MEMBER_ID);
        Member member = createMemberWithToken(token);
        String storedHash = member.getRefreshTokenHash();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        Member foundMember = refreshTokenService.findMemberByValidToken(token);

        assertThat(foundMember).isSameAs(member);
        assertThat(foundMember.getRefreshTokenHash()).isEqualTo(storedHash);
        assertThat(jwtProvider.parseRefreshToken(token).expiresAt()).isEqualTo(NOW.plus(REFRESH_EXPIRATION));
        verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        verify(memberRepository, never()).save(any(Member.class));
    }

    // JWT가 정상이더라도 해당 회원이 존재하지 않으면 거부한다.
    @Test
    void rejectsTokenForUnknownMember() {
        String token = jwtProvider.createRefreshToken(MEMBER_ID);

        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 서명이 정상이어도 DB에 저장된 최신 JWT 해시와 다르면 사용할 수 없다.
    @Test
    void rejectsValidJwtWithDifferentStoredHash() {
        String token = jwtProvider.createRefreshToken(MEMBER_ID);
        String otherToken = jwtProvider.createRefreshToken(MEMBER_ID);
        Member member = createMemberWithToken(otherToken);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(member.getRefreshTokenHash()).isEqualTo(hasher.hash(otherToken));
    }

    // 만료된 JWT는 회원 DB를 조회하기 전에 거부한다.
    @Test
    void rejectsExpiredTokenBeforeDatabaseLookup() {
        JwtProvider earlierProvider = new JwtProvider(properties,
                Clock.fixed(NOW.minus(REFRESH_EXPIRATION).minusSeconds(1), ZoneOffset.UTC));
        String token = earlierProvider.createRefreshToken(MEMBER_ID);

        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_EXPIRED);
        verifyNoInteractions(memberRepository);
    }

    // 만료 시각에 정확히 도달한 JWT도 DB 조회 전에 거부한다.
    @Test
    void rejectsTokenAtExactExpirationBeforeDatabaseLookup() {
        JwtProvider earlierProvider = new JwtProvider(properties,
                Clock.fixed(NOW.minus(REFRESH_EXPIRATION), ZoneOffset.UTC));
        String token = earlierProvider.createRefreshToken(MEMBER_ID);

        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_EXPIRED);
        verifyNoInteractions(memberRepository);
    }

    // JWT 검증 당시에는 유효했어도 회원 행 잠금을 기다리는 사이 만료되면 거부한다.
    @Test
    void checksExpirationAgainAfterLockLookupCompletes() {
        Instant expiresAt = NOW.plusSeconds(1);
        JwtProvider earlierProvider = new JwtProvider(properties,
                Clock.fixed(expiresAt.minus(REFRESH_EXPIRATION), ZoneOffset.UTC));
        String token = earlierProvider.createRefreshToken(MEMBER_ID);
        Member member = createMemberWithToken(token);
        AtomicReference<Instant> currentInstant = new AtomicReference<>(NOW);
        Clock advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenAnswer(invocation -> currentInstant.get());
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenAnswer(invocation -> {
            currentInstant.set(expiresAt);
            return Optional.of(member);
        });
        JwtProvider provider = new JwtProvider(properties, advancingClock);
        RefreshTokenService service = new RefreshTokenService(memberRepository, provider, hasher, advancingClock);

        assertErrorCode(() -> service.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_EXPIRED);
        assertThat(member.getRefreshTokenHash()).isEqualTo(hasher.hash(token));
    }

    // JWT 만료 시간이 남았더라도 저장된 해시가 삭제되었으면 사용할 수 없다.
    @Test
    void rejectsRevokedToken() {
        String token = jwtProvider.createRefreshToken(MEMBER_ID);
        Member member = createMember();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 누락되거나 JWT 형식이 아닌 토큰은 회원 DB 조회 전에 거부한다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t", "not-a-jwt"})
    void rejectsMissingOrMalformedTokenBeforeDatabaseLookup(String token) {
        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_INVALID);
        verifyNoInteractions(memberRepository);
    }

    // Access Token은 재발급 자격 증명이 아니므로 DB 조회 전에 거부한다.
    @Test
    void rejectsAccessTokenBeforeDatabaseLookup() {
        String accessToken = jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);

        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(accessToken), ErrorCode.REFRESH_TOKEN_INVALID);
        verifyNoInteractions(memberRepository);
    }

    // 현재 Refresh JWT의 해시를 삭제하면 이후 같은 토큰의 재발급 요청도 거부한다.
    @Test
    void deletesHashAndRejectsSubsequentValidation() {
        String token = jwtProvider.createRefreshToken(MEMBER_ID);
        Member member = createMemberWithToken(token);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        refreshTokenService.revoke(token);

        assertThat(member.getRefreshTokenHash()).isNull();
        assertErrorCode(() -> refreshTokenService.findMemberByValidToken(token), ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 아직 만료되지 않은 정상 JWT로 반복 로그아웃해도 해시 삭제 상태를 그대로 유지한다.
    @Test
    void repeatedRevocationIsIdempotent() {
        String token = jwtProvider.createRefreshToken(MEMBER_ID);
        Member member = createMemberWithToken(token);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        refreshTokenService.revoke(token);
        refreshTokenService.revoke(token);

        assertThat(member.getRefreshTokenHash()).isNull();
    }

    // 이전 로그인 JWT로 로그아웃하더라도 새 로그인 토큰의 해시를 삭제하지 않는다.
    @Test
    void previousTokenCannotRevokeLatestToken() {
        String previousToken = jwtProvider.createRefreshToken(MEMBER_ID);
        String latestToken = jwtProvider.createRefreshToken(MEMBER_ID);
        Member member = createMemberWithToken(latestToken);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        assertErrorCode(() -> refreshTokenService.revoke(previousToken), ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(member.getRefreshTokenHash()).isEqualTo(hasher.hash(latestToken));
        assertThat(refreshTokenService.findMemberByValidToken(latestToken)).isSameAs(member);
    }

    // 회원이 삭제되었다면 정상 JWT를 가지고 있더라도 로그아웃 처리를 수행하지 않는다.
    @Test
    void rejectsRevocationForUnknownMember() {
        String token = jwtProvider.createRefreshToken(MEMBER_ID);

        assertErrorCode(() -> refreshTokenService.revoke(token), ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // Access Token을 전달한 로그아웃 요청은 회원의 Refresh Token 상태에 접근하지 않는다.
    @Test
    void rejectsRevocationWithAccessTokenBeforeDatabaseLookup() {
        String accessToken = jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);

        assertErrorCode(() -> refreshTokenService.revoke(accessToken), ErrorCode.REFRESH_TOKEN_INVALID);
        verifyNoInteractions(memberRepository);
    }

    private Member createMember() {
        return Member.builder()
                .email("member@example.com")
                .name("회원")
                .passwordHash("hashed-password")
                .build();
    }

    private Member createMemberWithToken(String token) {
        Member member = createMember();
        member.replaceRefreshToken(hasher.hash(token));
        return member;
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        CustomException error = assertThrows(CustomException.class, action::run);
        assertThat(error.getErrorCode()).isEqualTo(expected);
        assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
