package com.ceos24.cgv.global.security.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    TOKEN_NOT_EXIST(
            HttpStatus.UNAUTHORIZED,
            "TOKEN_NOT_EXIST",
            "인증 토큰이 필요합니다."
    ),

    TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "TOKEN_EXPIRED",
            "토큰이 만료되었습니다."
    ),

    TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
            "TOKEN_INVALID",
            "유효하지 않은 토큰입니다."
    ),
    ACCESS_DENIED(
            HttpStatus.BAD_REQUEST,
            "ACCESS_DENIED",
            "액세스가 제한되었습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
