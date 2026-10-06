package com.ceos24.cgv.domain.user.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER404",
            "존재하지 않는 사용자입니다."
    ),

    DUPLICATE_LOGIN_ID(
            HttpStatus.CONFLICT,
            "USER409",
                    "이미 사용 중인 아이디입니다."
    ),

    INVALID_LOGIN(
            HttpStatus.UNAUTHORIZED,
            "AUTH401",
                    "아이디 또는 비밀번호가 올바르지 않습니다."
    );


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}