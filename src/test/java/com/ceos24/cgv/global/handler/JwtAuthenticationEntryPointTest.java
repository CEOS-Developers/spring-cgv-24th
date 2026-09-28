package com.ceos24.cgv.global.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationEntryPointTest {

    private JwtAuthenticationEntryPoint authenticationEntryPoint;

    @BeforeEach
    void setUp() {
        authenticationEntryPoint = new JwtAuthenticationEntryPoint(new ObjectMapper());
    }

    @Test
    void 인증되지_않은_요청은_401_JSON을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new InsufficientAuthenticationException("인증 정보 없음")
        );

        assertEquals(401, response.getStatus());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(response.getContentType())
        ));
        assertTrue(response.getContentAsString().contains("AUTH004"));
        assertTrue(response.getContentAsString().contains("인증 토큰이 필요합니다."));
    }
}
