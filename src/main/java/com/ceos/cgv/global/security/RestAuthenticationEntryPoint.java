package com.ceos.cgv.global.security;

import com.ceos.cgv.global.security.exception.SecurityErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    static final String ERROR_ATTRIBUTE = RestAuthenticationEntryPoint.class.getName() + ".error";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityErrorCode error = (SecurityErrorCode) request.getAttribute(ERROR_ATTRIBUTE);
        SecurityErrorWriter.write(response, error == null ? SecurityErrorCode.TOKEN_NOT_EXIST : error);
    }
}
