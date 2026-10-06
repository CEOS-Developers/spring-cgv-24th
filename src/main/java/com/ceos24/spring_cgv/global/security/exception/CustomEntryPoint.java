package com.ceos24.spring_cgv.global.security.exception;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apipayload.code.GeneralErrorCode;
import com.ceos24.spring_cgv.global.apipayload.exception.ProjectException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class CustomEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public CustomEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {

        Object exception = request.getAttribute("exception");
        BaseErrorCode errorCode = (exception instanceof BaseErrorCode ec) ? ec : GeneralErrorCode.UNAUTHORIZED;

        resolver.resolveException(request, response, null, new ProjectException(errorCode));
    }
}
