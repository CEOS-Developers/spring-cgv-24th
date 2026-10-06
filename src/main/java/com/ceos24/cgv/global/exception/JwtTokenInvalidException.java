package com.ceos24.cgv.global.exception;

// 유효하지 않은 토큰 예외
public class JwtTokenInvalidException extends RuntimeException {
    public JwtTokenInvalidException() {
        super("유효하지 않은 토큰입니다.");
    }

    public JwtTokenInvalidException(Throwable cause) {
        super("유효하지 않은 토큰입니다.", cause);
    }
}
