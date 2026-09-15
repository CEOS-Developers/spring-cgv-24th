package com.ceos.cgv.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CINEMA_NOT_FOUND(HttpStatus.NOT_FOUND, "영화관을 찾을 수 없습니다."),
    SCREEN_NOT_FOUND(HttpStatus.NOT_FOUND, "상영관을 찾을 수 없습니다."),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "영화를 찾을 수 없습니다."),
    SCREENING_NOT_FOUND(HttpStatus.NOT_FOUND, "상영 일정을 찾을 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다."),
    RESERVATION_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "매점 상품을 찾을 수 없습니다."),
    INVALID_SEAT(HttpStatus.BAD_REQUEST, "상영관에 존재하지 않는 좌석입니다."),
    DUPLICATE_SEAT_IN_REQUEST(HttpStatus.BAD_REQUEST, "요청에 같은 좌석이 중복되어 있습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예매된 좌석입니다."),
    DUPLICATE_LIKE(HttpStatus.CONFLICT, "이미 찜한 항목입니다."),
    STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "상품 재고가 부족합니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
