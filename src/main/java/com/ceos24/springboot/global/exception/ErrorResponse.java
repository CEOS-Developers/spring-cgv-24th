package com.ceos24.springboot.global.exception;

import org.springframework.http.HttpStatus;

public record ErrorResponse(
        int status,
        String message
) {

//    정적 팩트로 메서드
    public static ErrorResponse of(
            HttpStatus status,
            String message
    ) {
        return new ErrorResponse(
                status.value(),
                message
        );
    }
}