package com.ceos.cgv.global.security.handler;

import com.ceos.cgv.global.security.exception.SecurityErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    public static final String ERROR_ATTRIBUTE = RestAuthenticationEntryPoint.class.getName() + ".error";
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityErrorCode error = (SecurityErrorCode) request.getAttribute(ERROR_ATTRIBUTE);
        SecurityErrorWriter.write(response, objectMapper,
                error == null ? SecurityErrorCode.TOKEN_NOT_EXIST : error);
    }
}
