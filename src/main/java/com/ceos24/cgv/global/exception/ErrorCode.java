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
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "영화를 찾을 수 없습니다."),
    SCREENING_NOT_FOUND(HttpStatus.NOT_FOUND, "상영 정보를 찾을 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매 정보를 찾을 수 없습니다."),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "지점을 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),

    // 인증
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    MALFORMED_TOKEN(HttpStatus.UNAUTHORIZED, "형식이 올바르지 않은 토큰입니다."),

    // 예매
    SEAT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "좌석이 상영관 범위를 벗어났습니다."),
    DUPLICATE_SEAT_IN_REQUEST(HttpStatus.BAD_REQUEST, "요청 안에 중복된 좌석이 있습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 선택된 좌석입니다."),
    SEAT_RESERVATION_CONFLICT(HttpStatus.CONFLICT, "좌석 선점 경합이 발생했습니다. 다시 시도해 주세요."),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    RESERVATION_NOT_PENDING(HttpStatus.CONFLICT, "결제 대기 상태인 예매가 아닙니다."),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "선점 시간이 만료된 예매입니다."),
    CANCEL_DEADLINE_PASSED(HttpStatus.CONFLICT, "상영 20분 전까지만 취소할 수 있습니다."),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제에 실패했습니다. 좌석 선택부터 다시 진행해 주세요."),

    // 찜
    LIKE_REQUEST_CONFLICT(HttpStatus.CONFLICT, "찜 요청이 동시에 처리되었습니다. 다시 시도해 주세요."),

    // 매점
    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "재고는 1개 이상이어야 합니다."),
    DUPLICATE_PRODUCT_IN_REQUEST(HttpStatus.BAD_REQUEST, "요청 안에 중복된 상품이 있습니다."),
    BRANCH_NOT_OPERATING(HttpStatus.CONFLICT, "운영 중인 지점이 아닙니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    STOCK_LOCK_CONFLICT(HttpStatus.CONFLICT, "주문이 몰려 처리하지 못했습니다. 다시 시도해 주세요."),
    PURCHASE_PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
