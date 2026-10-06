package com.ceos24.springboot.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
//    어떤 에러인지 정의

    // Movie
    MOVIE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 영화를 찾을 수 없습니다."
    ),

    // Theater
    THEATER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 영화관을 찾을 수 없습니다."
    ),

    // Screening
    SCREENING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 상영회차를 찾을 수 없습니다."
    ),

    // Reservation
    RESERVATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 예매를 찾을 수 없습니다."
    ),

    ALREADY_CANCELED_RESERVATION(
            HttpStatus.BAD_REQUEST,
            "이미 취소된 예매입니다."
    ),


    // Authentication & Authorization
    TOKEN_NOT_EXIST(
            HttpStatus.UNAUTHORIZED,
        "토큰이 존재하지 않습니다."
    ),

    TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
        "토큰이 만료되었습니다."
    ),

    TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
        "유효하지 않은 토큰입니다."
    ),

    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
        "접근 권한이 없습니다."
    );

    private final HttpStatus status;
    private final String message;
}