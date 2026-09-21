package com.ceos.cgv.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 로그인 아이디입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 회원 정보입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_NOT_EXIST(HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CINEMA_NOT_FOUND(HttpStatus.NOT_FOUND, "영화관을 찾을 수 없습니다."),
    SCREEN_NOT_FOUND(HttpStatus.NOT_FOUND, "상영관을 찾을 수 없습니다."),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "영화를 찾을 수 없습니다."),
    MOVIE_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 이용할 수 없는 영화입니다."),
    SCREENING_NOT_FOUND(HttpStatus.NOT_FOUND, "상영 일정을 찾을 수 없습니다."),
    SCREENING_SEATS_NOT_READY(HttpStatus.CONFLICT, "상영 좌석이 준비되지 않았습니다."),
    SEAT_MIGRATION_CONFLICT(HttpStatus.CONFLICT, "기존 좌석 이력을 자동으로 연결할 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다."),
    RESERVATION_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "좌석 선점 시간이 만료되었습니다."),
    HOLD_NOT_ACTIVE(HttpStatus.CONFLICT, "진행 중인 좌석 선점이 아닙니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "매점 상품을 찾을 수 없습니다."),
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "영화관의 상품 재고를 찾을 수 없습니다."),
    FOOD_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "매점 주문을 찾을 수 없습니다."),
    DUPLICATE_INVENTORY(HttpStatus.CONFLICT, "해당 영화관의 상품 재고가 이미 등록되어 있습니다."),
    INVALID_SEAT(HttpStatus.BAD_REQUEST, "상영관에 존재하지 않는 좌석입니다."),
    DUPLICATE_SEAT_IN_REQUEST(HttpStatus.BAD_REQUEST, "요청에 같은 좌석이 중복되어 있습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예매된 좌석입니다."),
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
