package com.ceos24.cgv.global.security.handler;

import com.ceos24.cgv.global.security.exception.SecurityErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static com.ceos24.cgv.global.security.handler.SecurityResponseWriter.AUTH_ERROR;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        SecurityErrorCode errorCode =
                (SecurityErrorCode) request.getAttribute(
                        AUTH_ERROR
                );

        if (errorCode == null) {
            errorCode =
                    SecurityErrorCode.TOKEN_NOT_EXIST;
        }

        SecurityResponseWriter.write(
                response,
                objectMapper,
                errorCode
        );
    }
}
