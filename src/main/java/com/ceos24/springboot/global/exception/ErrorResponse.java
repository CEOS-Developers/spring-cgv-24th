package com.ceos24.springboot.global.exception;

import org.springframework.http.HttpStatus;

// 클라이언트에게 반환할 JSON 형태
public record ErrorResponse(
        int status,
        String code,
        String message
) {

    // ErrorCode 기반 응답
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage()
        );
    }

    // 기존 예외 처리를 위한 응답
    public static ErrorResponse of(
            HttpStatus status,
            String message
    ) {
        return new ErrorResponse(
                status.value(),
                status.name(),
                message
        );
    }
}