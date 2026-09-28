package com.ceos24.cgv.global.exception;

// JWT 만료 예외
public class JwtTokenExpiredException extends RuntimeException {
    public JwtTokenExpiredException(Throwable cause) {
        super("토큰이 만료되었습니다.", cause);
    }
}
