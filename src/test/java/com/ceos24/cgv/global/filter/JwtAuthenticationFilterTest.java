package com.ceos24.cgv.global.filter;

import com.ceos24.cgv.global.exception.JwtTokenExpiredException;
import com.ceos24.cgv.global.exception.JwtTokenInvalidException;
import com.ceos24.cgv.global.jwt.JwtTokenClaims;
import com.ceos24.cgv.global.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 토큰이_없으면_인증하지_않고_다음_필터로_진행한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).parseAccessToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 정상_Access_Token이면_SecurityContext에_인증_정보를_저장한다() throws Exception {
        MockHttpServletRequest request = bearerRequest("valid-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(jwtTokenProvider.parseAccessToken("valid-access-token"))
                .thenReturn(new JwtTokenClaims("test-user", "ROLE_USER"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("test-user", authentication.getName());
        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void 만료된_Token이면_401_응답을_반환하고_요청을_중단한다() throws Exception {
        MockHttpServletRequest request = bearerRequest("expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(jwtTokenProvider.parseAccessToken("expired-token"))
                .thenThrow(new JwtTokenExpiredException(new RuntimeException()));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH002"));
        assertFalse(response.isCommitted() && response.getContentAsString().isBlank());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void 유효하지_않은_Token이면_401_응답을_반환하고_요청을_중단한다() throws Exception {
        MockHttpServletRequest request = bearerRequest("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(jwtTokenProvider.parseAccessToken("invalid-token"))
                .thenThrow(new JwtTokenInvalidException());

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH003"));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void Bearer_Token_값이_비어있으면_유효하지_않은_Token으로_처리한다() throws Exception {
        MockHttpServletRequest request = bearerRequest("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH003"));
        verify(filterChain, never()).doFilter(request, response);
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
