package com.ceos24.cgv.global.exception;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors
) {

    public record FieldError(String field, String value, String reason) {}

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), errors);
    }
}
