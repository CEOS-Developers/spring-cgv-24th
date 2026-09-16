package com.ceos24.cgv.domain.theater.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserTheaterErrorCode implements BaseErrorCode {

    THEATER_ALREADY_LIKED(
            HttpStatus.CONFLICT,
            "USER_THEATER409",
            "이미 찜한 영화관입니다."
    ),

    THEATER_LIKE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER_THEATER404",
            "찜하지 않은 영화관입니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}