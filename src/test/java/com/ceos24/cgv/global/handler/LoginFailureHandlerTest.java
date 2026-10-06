package com.ceos24.cgv.global.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFailureHandlerTest {

    private LoginFailureHandler loginFailureHandler;

    @BeforeEach
    void setUp() {
        loginFailureHandler = new LoginFailureHandler(new ObjectMapper());
    }

    @Test
    void 없는_계정과_잘못된_비밀번호는_동일한_응답을_반환한다() throws Exception {
        MockHttpServletResponse userNotFoundResponse = executeFailure(
                new UsernameNotFoundException("존재하지 않는 계정")
        );
        MockHttpServletResponse badCredentialsResponse = executeFailure(
                new BadCredentialsException("잘못된 비밀번호")
        );

        assertEquals(401, userNotFoundResponse.getStatus());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(userNotFoundResponse.getContentType())
        ));
        assertEquals(
                userNotFoundResponse.getContentAsString(),
                badCredentialsResponse.getContentAsString()
        );
        assertTrue(userNotFoundResponse.getContentAsString().contains("AUTH001"));
        assertTrue(userNotFoundResponse.getContentAsString()
                .contains("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    private MockHttpServletResponse executeFailure(AuthenticationException exception) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        loginFailureHandler.onAuthenticationFailure(
                request,
                response,
                exception
        );

        return response;
    }
}
