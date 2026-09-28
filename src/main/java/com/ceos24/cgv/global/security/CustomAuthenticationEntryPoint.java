package com.ceos24.cgv.global.security;

import com.ceos24.cgv.global.security.exception.AuthErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor

//401 처리
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        responseWriter.write(response, AuthErrorCode.TOKEN_NOT_EXIST);
    }

    // JWT 필터에서 만료·변조 원인을 알고 있을 때 호출
    public void commence(
            HttpServletResponse response,
            AuthErrorCode errorCode
    ) throws IOException {
        responseWriter.write(response, errorCode);
    }
}