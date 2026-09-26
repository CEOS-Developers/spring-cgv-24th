package com.ceos.cgv.global.security;

import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class SecurityErrorWriter {
    private SecurityErrorWriter() {
    }

    static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"status":%d,"code":"%s","message":"%s"}
                """.formatted(errorCode.httpStatus().value(), errorCode.name(), errorCode.message()).trim());
    }
}
