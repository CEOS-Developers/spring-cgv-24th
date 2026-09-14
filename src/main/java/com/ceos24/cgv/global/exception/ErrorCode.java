package com.ceos24.cgv.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 조회 실패
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "영화를 찾을 수 없습니다."),
    SCREENING_NOT_FOUND(HttpStatus.NOT_FOUND, "상영 정보를 찾을 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매 정보를 찾을 수 없습니다."),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "지점을 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),

    // 예매
    SEAT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "좌석이 상영관 범위를 벗어났습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예매된 좌석입니다."),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),

    // 매점
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
