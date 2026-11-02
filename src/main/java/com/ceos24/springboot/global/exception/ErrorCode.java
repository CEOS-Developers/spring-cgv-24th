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
    SEAT_UNAVAILABLE(
            HttpStatus.CONFLICT,
            "이미 예매되었거나 다른 사용자가 선택 중인 좌석입니다."
    ),

    RESERVATION_EXPIRED(
            HttpStatus.CONFLICT,
            "좌석 점유 시간 10분이 만료되었습니다. 좌석을 다시 선택해 주세요."
    ),

    INVALID_RESERVATION_STATUS(
            HttpStatus.CONFLICT,
            "현재 상태에서는 예매를 확정할 수 없습니다."
    ),

    RESERVATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 예매를 찾을 수 없습니다."
    ),

    ALREADY_CANCELED_RESERVATION(
            HttpStatus.BAD_REQUEST,
            "이미 취소된 예매입니다."
    ),

    // Payment
    PAYMENT_USER_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "결제 사용자가 필요합니다."
    ),

    PAYMENT_TARGET_TYPE_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "결제 대상 유형이 필요합니다."
    ),

    INVALID_PAYMENT_TARGET_ID(
            HttpStatus.BAD_REQUEST,
            "유효한 결제 대상 ID가 필요합니다."
    ),

    INVALID_PAYMENT_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "결제 금액은 0보다 커야 합니다."
    ),

    INVALID_PAYMENT_EXPIRATION(
            HttpStatus.BAD_REQUEST,
            "결제 만료 시각은 생성 시각보다 이후여야 합니다."
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
    ),

    LOGIN_FAILED(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    // Signup
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
        "이미 사용 중인 이메일입니다."
    );

    private final HttpStatus status;
    private final String message;
}