package com.ceos.cgv.global.security.handler;

import com.ceos.cgv.global.exception.ErrorResponse;
import com.ceos.cgv.global.security.exception.SecurityErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class SecurityErrorWriter {
    private SecurityErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper,
                      SecurityErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                errorCode.httpStatus().value(), errorCode.name(), errorCode.message()));
    }
}
