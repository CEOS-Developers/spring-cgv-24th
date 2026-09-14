package com.ceos.cgv.global.exception;

public record ErrorResponse(
        int status,
        String message
) {
}
