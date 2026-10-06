package com.ceos24.cgv.global.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAccessDeniedHandlerTest {

    private JwtAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        accessDeniedHandler = new JwtAccessDeniedHandler(new ObjectMapper());
    }

    @Test
    void 권한이_부족한_요청은_403_JSON을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("권한 부족")
        );

        assertEquals(403, response.getStatus());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(response.getContentType())
        ));
        assertTrue(response.getContentAsString().contains("AUTH005"));
        assertTrue(response.getContentAsString().contains("해당 요청에 접근할 권한이 없습니다."));
    }
}
