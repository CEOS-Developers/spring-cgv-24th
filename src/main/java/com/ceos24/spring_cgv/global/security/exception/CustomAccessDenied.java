package com.ceos24.spring_cgv.global.security.exception;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apipayload.code.GeneralErrorCode;
import com.ceos24.spring_cgv.global.apipayload.exception.ProjectException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class CustomAccessDenied implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public CustomAccessDenied(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        BaseErrorCode errorCode = GeneralErrorCode.FORBIDDEN;

        resolver.resolveException(request, response, null, new ProjectException(errorCode));
    }
}
