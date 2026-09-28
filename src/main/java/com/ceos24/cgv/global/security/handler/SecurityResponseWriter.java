package com.ceos24.cgv.global.security.handler;

import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.security.exception.SecurityErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class SecurityResponseWriter {

    public static final String AUTH_ERROR =
            "AUTH_ERROR_CODE";

    private SecurityResponseWriter() {
    }

    public static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            SecurityErrorCode errorCode
    ) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(
                errorCode.getHttpStatus().value()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        ApiResponse<Object> errorResponse =
                ApiResponse.onFailure(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        null
                );

        objectMapper.writeValue(
                response.getWriter(),
                errorResponse
        );
    }
}
