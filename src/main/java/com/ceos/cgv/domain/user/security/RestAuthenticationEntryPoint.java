package com.ceos.cgv.domain.user.security;

import com.ceos.cgv.global.exception.ErrorCode;
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
        ErrorCode error = (ErrorCode) request.getAttribute(ERROR_ATTRIBUTE);
        SecurityErrorWriter.write(response, error == null ? ErrorCode.TOKEN_NOT_EXIST : error);
    }
}
