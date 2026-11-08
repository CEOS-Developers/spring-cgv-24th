package com.ceos24.cgv.domain.auth.service;

import com.ceos24.cgv.domain.auth.dto.request.LoginRequest;
import com.ceos24.cgv.domain.auth.dto.response.LoginResponse;
import com.ceos24.cgv.domain.user.exception.UserErrorStatus;
import com.ceos24.cgv.global.exception.GeneralException;
import com.ceos24.cgv.global.security.CustomUserDetails;
import com.ceos24.cgv.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static com.ceos24.cgv.domain.user.enums.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private final LoginRequest request = new LoginRequest("user1", "password1!");

    @Test
    void 인증에_성공하면_액세스_토큰을_반환한다() {
        CustomUserDetails userDetails = new CustomUserDetails(1L, USER);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities()));
        when(jwtTokenProvider.createAccessToken(userDetails)).thenReturn("access-token");

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void 자격_증명이_틀리면_LOGIN_FAILED로_변환한다() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorReasonHttpStatus().getCode())
                                .isEqualTo(UserErrorStatus.LOGIN_FAILED.getCode()));
    }

    @Test
    void 내부_인증_오류는_LOGIN_FAILED로_바꾸지_않고_그대로_던진다() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new InternalAuthenticationServiceException("db down"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InternalAuthenticationServiceException.class);
    }
}
