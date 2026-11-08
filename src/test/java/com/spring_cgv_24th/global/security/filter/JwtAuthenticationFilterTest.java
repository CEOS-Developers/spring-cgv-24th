package com.spring_cgv_24th.global.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spring_cgv_24th.domain.auth.controller.AuthController;
import com.spring_cgv_24th.domain.auth.dto.AuthReqDTO;
import com.spring_cgv_24th.domain.auth.dto.AuthResDTO;
import com.spring_cgv_24th.domain.auth.service.AuthService;
import com.spring_cgv_24th.domain.member.enums.MemberRole;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import com.spring_cgv_24th.global.exception.GlobalExceptionHandler;
import com.spring_cgv_24th.global.jwt.AccessTokenClaims;
import com.spring_cgv_24th.global.jwt.JwtProperties;
import com.spring_cgv_24th.global.jwt.JwtProvider;
import com.spring_cgv_24th.global.security.handler.CustomAccessDeniedHandler;
import com.spring_cgv_24th.global.security.handler.CustomAuthenticationEntryPoint;
import com.spring_cgv_24th.global.security.principal.CustomUserDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock private JwtProvider jwtProvider;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider);
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Authorization 헤더가 없으면 인증을 만들지 않고 다음 필터로 넘긴다.
    @Test
    @DisplayName("Bearer Token이 없으면 인증 없이 다음 필터로 진행한다")
    void noToken() throws Exception {
        MockHttpServletRequest request = request("/api/reservations");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtProvider);
        verify(filterChain).doFilter(request, response);
    }

    // 검증된 Claim을 SecurityContext의 인증 객체와 ROLE_USER 권한으로 옮긴다.
    @Test
    @DisplayName("검증된 토큰으로 인증 객체를 새 SecurityContext에 저장한다")
    void validToken() throws Exception {
        MockHttpServletRequest request = request("/api/reservations");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        when(jwtProvider.parseAccessToken("access-token"))
                .thenReturn(new AccessTokenClaims(1L, MemberRole.USER));

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        assertThat(principal.getMemberId()).isEqualTo(1L);
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    // Refresh JWT는 Access 인증을 만들지 않고 실패 원인만 저장해 다음 필터로 넘긴다.
    @Test
    @DisplayName("Refresh Token으로 보호 API에 접근하면 인증 실패로 처리한다")
    void rejectsRealRefreshTokenOnProtectedApi() throws Exception {
        JwtProperties properties = new JwtProperties(
                Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()),
                "spring-cgv-24th", "spring-cgv-api", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtProvider realProvider = new JwtProvider(properties, Clock.systemUTC());
        JwtAuthenticationFilter realFilter = new JwtAuthenticationFilter(realProvider);
        MockHttpServletRequest request = request("/api/reservations");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + realProvider.createRefreshToken(1L));

        realFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(ErrorCode.TOKEN_INVALID);
        verify(filterChain).doFilter(request, response);
    }

    // 만료된 토큰은 인증을 비우고 실패 원인을 보관한 뒤 다음 필터로 넘긴다.
    @Test
    @DisplayName("검증 실패 원인을 요청에 저장하고 인증 없이 체인을 진행한다")
    void invalidToken() throws Exception {
        MockHttpServletRequest request = request("/api/reservations");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
        when(jwtProvider.parseAccessToken("expired-token"))
                .thenThrow(new CustomException(ErrorCode.TOKEN_EXPIRED));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
        verify(filterChain).doFilter(request, response);
    }

    // Bearer가 아닌 인증 방식은 토큰 누락이 아닌 무효한 헤더로 취급한다.
    @Test
    @DisplayName("Bearer 형식이 아닌 인증 헤더는 TOKEN_INVALID로 처리한다")
    void invalidAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = request("/api/admin/check");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtProvider);
        verify(filterChain).doFilter(request, response);
    }

    // 공개 API의 잘못된 JWT는 인증을 만들지 않으며, 익명 접근 여부는 뒤의 Security 체인이 결정한다.
    @Test
    @DisplayName("공개 API의 잘못된 토큰은 인증 없이 다음 필터로 넘긴다")
    void invalidTokenOnPublicApi() throws Exception {
        MockHttpServletRequest request = request("/api/movies");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        when(jwtProvider.parseAccessToken("invalid-token"))
                .thenThrow(new CustomException(ErrorCode.TOKEN_INVALID));

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getContentAsString()).isEmpty();
        verify(filterChain).doFilter(request, response);
    }

    // 실제 EntryPoint와 공통 예외 처리기를 연결해 토큰 오류의 JSON 코드와 401 상태를 검증한다.
    @ParameterizedTest
    @CsvSource({
            "TOKEN_NOT_EXIST, TOKEN_NOT_EXIST401",
            "TOKEN_EXPIRED, TOKEN_EXPIRED401",
            "TOKEN_INVALID, TOKEN_INVALID401"
    })
    void authenticationFailureReturnsStatusSuffixedCode(ErrorCode errorCode, String expectedCode)
            throws Exception {
        MockHttpServletRequest request = request("/api/reservations");
        if (errorCode != ErrorCode.TOKEN_NOT_EXIST) {
            request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE, errorCode);
        }
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(exceptionResolver());

        entryPoint.commence(request, response, new InsufficientAuthenticationException("인증이 필요합니다."));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var json = JSON_MAPPER.readTree(response.getContentAsString());
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("code").asString()).isEqualTo(expectedCode);
        assertThat(json.path("message").asString()).isEqualTo(errorCode.getMessage());
        assertThat(json.has("data")).isFalse();
    }

    // 실제 권한 거부 핸들러도 상태·메시지를 유지하며 ACCESS_DENIED403을 공통 JSON에 담는다.
    @Test
    void accessDeniedReturnsStatusSuffixedCode() throws Exception {
        CustomAccessDeniedHandler deniedHandler = new CustomAccessDeniedHandler(exceptionResolver());

        deniedHandler.handle(request("/api/admin/check"), response, new AccessDeniedException("권한이 없습니다."));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var json = JSON_MAPPER.readTree(response.getContentAsString());
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("code").asString()).isEqualTo("ACCESS_DENIED403");
        assertThat(json.path("message").asString()).isEqualTo(ErrorCode.ACCESS_DENIED.getMessage());
        assertThat(json.has("data")).isFalse();
    }

    // 인증 API도 필터를 실행하지만 잘못된 Access 헤더 때문에 공개 POST 처리를 중단하지 않는다.
    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"})
    void publicAuthPathRecordsTokenFailureAndContinues(String path) throws Exception {
        MockHttpServletRequest request = request(path);
        request.setMethod("POST");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
        when(jwtProvider.parseAccessToken("expired-token"))
                .thenThrow(new CustomException(ErrorCode.TOKEN_EXPIRED));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
        verify(jwtProvider).parseAccessToken("expired-token");
        verify(filterChain).doFilter(request, response);
    }

    // 재발급 요청은 Access 인증 없이 본문을 서비스로 전달하며 만료된 Access 헤더가 있어도 진행한다.
    @ParameterizedTest(name = "만료된 Access Token 헤더 포함: {0}")
    @ValueSource(booleans = {false, true})
    void refreshRequestUsesBodyTokenRegardlessOfExpiredAccessHeader(boolean includeExpiredAccessHeader)
            throws Exception {
        Instant now = Instant.parse("2026-09-30T06:00:00Z");
        JwtProperties properties = new JwtProperties(
                Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()),
                "spring-cgv-24th", "spring-cgv-api", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtProvider realProvider = new JwtProvider(properties, Clock.fixed(now, ZoneOffset.UTC));
        JwtProvider oldIssuer = new JwtProvider(properties,
                Clock.fixed(now.minus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        JwtAuthenticationFilter realFilter = new JwtAuthenticationFilter(realProvider);
        AuthService authService = mock(AuthService.class);
        AuthReqDTO.RefreshReqDTO request = new AuthReqDTO.RefreshReqDTO(realProvider.createRefreshToken(1L));
        String newAccessToken = realProvider.createAccessToken(1L, MemberRole.USER);
        when(authService.refresh(request)).thenReturn(AuthResDTO.RefreshResDTO.from(newAccessToken));
        MockMvc mockMvc = authMvc(realFilter, authService);
        MockHttpServletRequestBuilder apiRequest = post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(request));
        if (includeExpiredAccessHeader) {
            apiRequest.header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + oldIssuer.createAccessToken(1L, MemberRole.USER));
        }

        mockMvc.perform(apiRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value(newAccessToken))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(authService).refresh(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // Access 헤더의 만료 오류가 있어도 본문 Refresh Token 검증 실패의 코드로 공통 JSON 401을 반환한다.
    @ParameterizedTest
    @CsvSource({
            "REFRESH_TOKEN_EXPIRED, REFRESH_TOKEN_EXPIRED401",
            "REFRESH_TOKEN_INVALID, REFRESH_TOKEN_INVALID401"
    })
    void refreshValidationFailureStillReturnsCommonJson(ErrorCode errorCode, String expectedCode) throws Exception {
        AuthService authService = mock(AuthService.class);
        AuthReqDTO.RefreshReqDTO request = new AuthReqDTO.RefreshReqDTO("invalid-refresh-token");
        when(authService.refresh(request)).thenThrow(new CustomException(errorCode));
        when(jwtProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new CustomException(ErrorCode.TOKEN_EXPIRED));

        authMvc(filter, authService).perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_MAPPER.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).refresh(request);
        verify(jwtProvider).parseAccessToken("expired-access-token");
    }

    // Access 인증을 요구하지 않는 재발급 API도 Refresh Token 필수 입력 검증은 서비스 실행 전에 적용한다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void refreshRequestRequiresNonBlankBodyToken(String refreshToken) throws Exception {
        AuthService authService = mock(AuthService.class);

        authMvc(filter, authService).perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_MAPPER.writeValueAsString(new AuthReqDTO.RefreshReqDTO(refreshToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(authService, jwtProvider);
    }

    // 로그아웃도 본문으로 자격 증명을 전달하므로 Access 헤더 누락·만료와 무관하게 서비스로 진행한다.
    @ParameterizedTest(name = "만료된 Access Token 헤더 포함: {0}")
    @ValueSource(booleans = {false, true})
    void logoutRequestUsesBodyTokenRegardlessOfExpiredAccessHeader(boolean includeExpiredAccessHeader)
            throws Exception {
        Instant now = Instant.parse("2026-09-30T06:00:00Z");
        JwtProperties properties = new JwtProperties(
                Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()),
                "spring-cgv-24th", "spring-cgv-api", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtProvider realProvider = new JwtProvider(properties, Clock.fixed(now, ZoneOffset.UTC));
        JwtProvider oldIssuer = new JwtProvider(properties,
                Clock.fixed(now.minus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        JwtAuthenticationFilter realFilter = new JwtAuthenticationFilter(realProvider);
        AuthService authService = mock(AuthService.class);
        AuthReqDTO.LogoutReqDTO request = new AuthReqDTO.LogoutReqDTO(realProvider.createRefreshToken(1L));
        MockHttpServletRequestBuilder apiRequest = post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(request));
        if (includeExpiredAccessHeader) {
            apiRequest.header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + oldIssuer.createAccessToken(1L, MemberRole.USER));
        }

        authMvc(realFilter, authService).perform(apiRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).logout(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // Access 헤더의 만료 오류가 있어도 로그아웃 본문 토큰의 만료·무효 코드로 공통 JSON 401을 반환한다.
    @ParameterizedTest
    @CsvSource({
            "REFRESH_TOKEN_EXPIRED, REFRESH_TOKEN_EXPIRED401",
            "REFRESH_TOKEN_INVALID, REFRESH_TOKEN_INVALID401"
    })
    void logoutValidationFailureStillReturnsCommonJson(ErrorCode errorCode, String expectedCode) throws Exception {
        AuthService authService = mock(AuthService.class);
        AuthReqDTO.LogoutReqDTO request = new AuthReqDTO.LogoutReqDTO("invalid-refresh-token");
        doThrow(new CustomException(errorCode)).when(authService).logout(request);
        when(jwtProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new CustomException(ErrorCode.TOKEN_EXPIRED));

        authMvc(filter, authService).perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_MAPPER.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).logout(request);
        verify(jwtProvider).parseAccessToken("expired-access-token");
    }

    // 로그아웃의 Refresh Token 누락·공백 입력은 필수 값 검증으로 서비스 실행 전에 공통 400으로 거부한다.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void logoutRequestRequiresNonBlankBodyToken(String refreshToken) throws Exception {
        AuthService authService = mock(AuthService.class);

        authMvc(filter, authService).perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_MAPPER.writeValueAsString(new AuthReqDTO.LogoutReqDTO(refreshToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(authService, jwtProvider);
    }

    private HandlerExceptionResolver exceptionResolver() {
        return authMvc(filter, mock(AuthService.class)).getDispatcherServlet()
                .getWebApplicationContext().getBean("handlerExceptionResolver", HandlerExceptionResolver.class);
    }

    private MockMvc authMvc(JwtAuthenticationFilter requestFilter, AuthService authService) {
        return MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(requestFilter)
                .build();
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
