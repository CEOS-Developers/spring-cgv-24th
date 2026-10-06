package com.spring_cgv_24th.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.auth.dto.AuthReqDTO;
import com.spring_cgv_24th.domain.auth.dto.AuthResDTO;
import com.spring_cgv_24th.domain.auth.token.RefreshTokenHasher;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import com.spring_cgv_24th.global.jwt.JwtProperties;
import com.spring_cgv_24th.global.jwt.JwtProvider;
import com.spring_cgv_24th.global.security.principal.CustomUserDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Password123!";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Captor private ArgumentCaptor<Member> memberCaptor;
    @Captor private ArgumentCaptor<Authentication> authenticationCaptor;
    @InjectMocks private AuthService authService;

    // 회원가입 시 비밀번호 원문 대신 인코딩 결과를 저장하고 역할을 USER로 고정한다.
    @Test
    void signUpSavesEncodedPasswordAndAlwaysUsesUserRole() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResDTO.SignUpResDTO response = authService.signUp(
                new AuthReqDTO.SignUpReqDTO(EMAIL, "테스트 회원", PASSWORD));

        verify(memberRepository).existsByEmail(EMAIL);
        verify(passwordEncoder).encode(PASSWORD);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertEquals(EMAIL, savedMember.getEmail());
        assertEquals("테스트 회원", savedMember.getName());
        assertEquals("encoded-password", savedMember.getPasswordHash());
        assertEquals(MemberRole.USER, savedMember.getRole());
        assertEquals(EMAIL, response.email());
        assertEquals(MemberRole.USER, response.role());
        verifyNoInteractions(authenticationManager, jwtProvider, refreshTokenService);
    }

    // 이미 등록된 이메일이면 인코딩이나 저장을 시도하지 않는다.
    @Test
    void duplicateEmailDoesNotEncodeOrSavePassword() {
        when(memberRepository.existsByEmail(EMAIL)).thenReturn(true);

        CustomException error = assertThrows(CustomException.class, () -> authService.signUp(
                new AuthReqDTO.SignUpReqDTO(EMAIL, "테스트 회원", PASSWORD)));

        assertEquals(ErrorCode.MEMBER_EMAIL_ALREADY_EXISTS, error.getErrorCode());
        verify(memberRepository, never()).save(any(Member.class));
        verifyNoInteractions(passwordEncoder, authenticationManager, jwtProvider, refreshTokenService);
    }

    // 인증된 ID·역할로 Access Token을 만들고 같은 회원의 Refresh Token을 JSON 응답에 함께 넣는다.
    @Test
    void loginAuthenticatesCredentialsAndIssuesBothTokensForPrincipal() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        Authentication authenticated = mock(Authentication.class);
        when(principal.getMemberId()).thenReturn(7L);
        when(principal.getRole()).thenReturn(MemberRole.ADMIN);
        when(authenticated.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated);
        when(jwtProvider.createAccessToken(7L, MemberRole.ADMIN)).thenReturn("access-token");
        when(refreshTokenService.issue(7L)).thenReturn("refresh-token");

        AuthResDTO.LoginResDTO response = authService.login(
                new AuthReqDTO.LoginReqDTO(EMAIL, PASSWORD));

        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        Authentication request = authenticationCaptor.getValue();
        assertTrue(request instanceof UsernamePasswordAuthenticationToken);
        assertFalse(request.isAuthenticated());
        assertEquals(EMAIL, request.getPrincipal());
        assertEquals(PASSWORD, request.getCredentials());
        verify(jwtProvider).createAccessToken(7L, MemberRole.ADMIN);
        verify(refreshTokenService).issue(7L);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        JsonNode json = JSON_MAPPER.valueToTree(response);
        assertEquals("access-token", json.path("accessToken").asString());
        assertEquals("refresh-token", json.path("refreshToken").asString());
        verifyNoInteractions(memberRepository, passwordEncoder);
    }

    // 실제 JWT 발급·해시 저장 로직을 연결하여 재로그인이 이전 Refresh Token을 대체하는지 확인한다.
    @Test
    void repeatedLoginStoresOnlyLatestRefreshTokenHash() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T06:00:00Z"), ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(
                Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()),
                "spring-cgv-24th", "spring-cgv-api", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtProvider realProvider = new JwtProvider(properties, clock);
        RefreshTokenHasher hasher = new RefreshTokenHasher();
        RefreshTokenService realRefreshService = new RefreshTokenService(
                memberRepository, realProvider, hasher, clock);
        AuthService service = new AuthService(
                memberRepository, passwordEncoder, authenticationManager, realProvider, realRefreshService);
        Member member = Member.builder()
                .email(EMAIL)
                .name("테스트 회원")
                .passwordHash("encoded-password")
                .role(MemberRole.USER)
                .build();
        CustomUserDetails principal = mock(CustomUserDetails.class);
        Authentication authenticated = mock(Authentication.class);
        when(principal.getMemberId()).thenReturn(7L);
        when(principal.getRole()).thenReturn(MemberRole.USER);
        when(authenticated.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(memberRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        AuthReqDTO.LoginReqDTO request = new AuthReqDTO.LoginReqDTO(EMAIL, PASSWORD);
        AuthResDTO.LoginResDTO first = service.login(request);

        assertEquals(7L, realProvider.parseAccessToken(first.accessToken()).memberId());
        assertEquals(MemberRole.USER, realProvider.parseAccessToken(first.accessToken()).role());
        assertEquals(7L, realProvider.parseRefreshToken(first.refreshToken()).memberId());
        assertEquals(hasher.hash(first.refreshToken()), member.getRefreshTokenHash());
        assertNotEquals(first.refreshToken(), member.getRefreshTokenHash());

        AuthResDTO.LoginResDTO second = service.login(request);

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertEquals(hasher.hash(second.refreshToken()), member.getRefreshTokenHash());
        CustomException error = assertThrows(CustomException.class,
                () -> realRefreshService.findMemberByValidToken(first.refreshToken()));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, error.getErrorCode());
        assertSame(member, realRefreshService.findMemberByValidToken(second.refreshToken()));
    }

    // Access Token이 만료되어도 유효한 Refresh Token으로 DB의 현재 권한을 반영하고 기존 Refresh는 유지한다.
    @Test
    void refreshAfterAccessExpirationUsesCurrentRoleWithoutRotatingRefreshToken() {
        Instant issuedAt = Instant.parse("2026-09-30T06:00:00Z");
        JwtProperties properties = new JwtProperties(
                Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()),
                "spring-cgv-24th", "spring-cgv-api", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtProvider issuer = new JwtProvider(properties, Clock.fixed(issuedAt, ZoneOffset.UTC));
        Clock currentClock = Clock.fixed(issuedAt.plus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        JwtProvider verifier = new JwtProvider(properties, currentClock);
        RefreshTokenHasher hasher = new RefreshTokenHasher();
        RefreshTokenService realRefreshService = new RefreshTokenService(
                memberRepository, verifier, hasher, currentClock);
        AuthService service = new AuthService(
                memberRepository, passwordEncoder, authenticationManager, verifier, realRefreshService);
        Member member = Member.builder()
                .email(EMAIL)
                .name("테스트 회원")
                .passwordHash("encoded-password")
                .role(MemberRole.ADMIN)
                .build();
        ReflectionTestUtils.setField(member, "id", 7L);
        String refreshToken = issuer.createRefreshToken(7L);
        String storedHash = hasher.hash(refreshToken);
        member.replaceRefreshToken(storedHash);
        when(memberRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        String expiredAccessToken = issuer.createAccessToken(7L, MemberRole.USER);

        CustomException expired = assertThrows(CustomException.class,
                () -> verifier.parseAccessToken(expiredAccessToken));
        assertEquals(ErrorCode.TOKEN_EXPIRED, expired.getErrorCode());

        AuthResDTO.RefreshResDTO response = service.refresh(new AuthReqDTO.RefreshReqDTO(refreshToken));

        assertNotEquals(expiredAccessToken, response.accessToken());
        assertEquals(7L, verifier.parseAccessToken(response.accessToken()).memberId());
        assertEquals(MemberRole.ADMIN, verifier.parseAccessToken(response.accessToken()).role());
        assertEquals(storedHash, member.getRefreshTokenHash());
        assertEquals(issuedAt.plus(Duration.ofDays(7)), verifier.parseRefreshToken(refreshToken).expiresAt());
        assertSame(member, realRefreshService.findMemberByValidToken(refreshToken));
        verifyNoInteractions(authenticationManager, passwordEncoder);
    }

    // 만료·폐기 등으로 Refresh 검증이 실패하면 같은 오류를 전달하고 Access Token을 발급하지 않는다.
    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"REFRESH_TOKEN_EXPIRED", "REFRESH_TOKEN_INVALID"})
    void invalidRefreshTokenDoesNotIssueAccessToken(ErrorCode errorCode) {
        when(refreshTokenService.findMemberByValidToken("invalid-refresh-token"))
                .thenThrow(new CustomException(errorCode));

        CustomException error = assertThrows(CustomException.class,
                () -> authService.refresh(new AuthReqDTO.RefreshReqDTO("invalid-refresh-token")));

        assertEquals(errorCode, error.getErrorCode());
        verifyNoInteractions(jwtProvider, memberRepository, authenticationManager, passwordEncoder);
    }

    // 로그아웃은 Refresh 해시를 삭제해 재발급을 막지만 Access는 유지하고, 같은 미만료 토큰의 반복 요청은 허용한다.
    @Test
    void logoutRevokesRefreshTokenButKeepsAccessTokenAndAllowsRepeatedLogout() {
        LogoutFixture fixture = logoutFixture();
        String accessToken = fixture.provider().createAccessToken(7L, MemberRole.USER);
        String refreshToken = fixture.provider().createRefreshToken(7L);
        fixture.member().replaceRefreshToken(fixture.hasher().hash(refreshToken));
        AuthReqDTO.LogoutReqDTO request = new AuthReqDTO.LogoutReqDTO(refreshToken);

        fixture.service().logout(request);

        assertNull(fixture.member().getRefreshTokenHash());
        CustomException error = assertThrows(CustomException.class,
                () -> fixture.service().refresh(new AuthReqDTO.RefreshReqDTO(refreshToken)));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, error.getErrorCode());
        assertDoesNotThrow(() -> fixture.service().logout(request));
        assertEquals(7L, fixture.provider().parseAccessToken(accessToken).memberId());
        verifyNoInteractions(authenticationManager, passwordEncoder);
    }

    // 이전 로그인 토큰으로 로그아웃을 시도해도 최신 토큰의 해시는 유지되고 재발급에도 사용할 수 있다.
    @Test
    void logoutWithPreviousRefreshTokenDoesNotRevokeLatestToken() {
        LogoutFixture fixture = logoutFixture();
        String previousToken = fixture.provider().createRefreshToken(7L);
        String latestToken = fixture.provider().createRefreshToken(7L);
        String latestHash = fixture.hasher().hash(latestToken);
        fixture.member().replaceRefreshToken(latestHash);

        CustomException error = assertThrows(CustomException.class,
                () -> fixture.service().logout(new AuthReqDTO.LogoutReqDTO(previousToken)));

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, error.getErrorCode());
        assertEquals(latestHash, fixture.member().getRefreshTokenHash());
        AuthResDTO.RefreshResDTO response = fixture.service().refresh(new AuthReqDTO.RefreshReqDTO(latestToken));
        assertEquals(7L, fixture.provider().parseAccessToken(response.accessToken()).memberId());
        verifyNoInteractions(authenticationManager, passwordEncoder);
    }

    // 만료되거나 유효하지 않은 Refresh Token의 폐기 오류는 그대로 전달하고 다른 인증 처리는 실행하지 않는다.
    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"REFRESH_TOKEN_EXPIRED", "REFRESH_TOKEN_INVALID"})
    void invalidLogoutTokenPropagatesRevocationFailure(ErrorCode errorCode) {
        doThrow(new CustomException(errorCode)).when(refreshTokenService).revoke("invalid-refresh-token");

        CustomException error = assertThrows(CustomException.class,
                () -> authService.logout(new AuthReqDTO.LogoutReqDTO("invalid-refresh-token")));

        assertEquals(errorCode, error.getErrorCode());
        verifyNoInteractions(jwtProvider, memberRepository, authenticationManager, passwordEncoder);
    }

    // 인증 실패는 LOGIN_FAILED로 변환하고 두 토큰 모두 발급하지 않아 기존 해시도 변경하지 않는다.
    @Test
    void badCredentialsReturnLoginFailedWithoutIssuingToken() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("인증 실패"));

        CustomException error = assertThrows(CustomException.class, () -> authService.login(
                new AuthReqDTO.LoginReqDTO(EMAIL, "wrong-password")));

        assertEquals(ErrorCode.LOGIN_FAILED, error.getErrorCode());
        verifyNoInteractions(jwtProvider, refreshTokenService, memberRepository, passwordEncoder);
    }

    private LogoutFixture logoutFixture() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T06:00:00Z"), ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(
                Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()),
                "spring-cgv-24th", "spring-cgv-api", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtProvider realProvider = new JwtProvider(properties, clock);
        RefreshTokenHasher hasher = new RefreshTokenHasher();
        RefreshTokenService realRefreshService = new RefreshTokenService(
                memberRepository, realProvider, hasher, clock);
        AuthService service = new AuthService(
                memberRepository, passwordEncoder, authenticationManager, realProvider, realRefreshService);
        Member member = Member.builder()
                .email(EMAIL)
                .name("테스트 회원")
                .passwordHash("encoded-password")
                .role(MemberRole.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", 7L);
        when(memberRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        return new LogoutFixture(service, realProvider, member, hasher);
    }

    private record LogoutFixture(
            AuthService service, JwtProvider provider, Member member, RefreshTokenHasher hasher
    ) {
    }
}
