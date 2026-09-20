package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.global.common.ApiResponse;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        log.warn("인증되지 않은 요청입니다. URI : {}", request.getRequestURI());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        ApiResponse<?> apiResponse = ApiResponse.error(401, ErrorCode.AUTH_UNAUTHORIZED_USER.getMessage());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
