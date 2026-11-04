package com.cgvclone.cgv.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    PAYMENT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "토스 주문서형 테스트 키 설정이 필요합니다."),
    INVALID_PAYMENT_REQUEST(HttpStatus.BAD_REQUEST, "결제 요청을 확인해주세요."),
    PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "주문 금액과 결제 금액이 일치하지 않습니다."),
    PAYMENT_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 주문 상태 또는 결제 키로 처리할 수 없습니다."),
    PAYMENT_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "결제 결과를 확인할 수 없습니다. 토스 결제 내역을 확인해주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    TOKEN_NOT_EXIST(HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_PURCHASE_REQUEST(HttpStatus.BAD_REQUEST, "영화관과 구매 상품을 확인해주세요. 상품은 중복 없이, 수량은 1 이상이어야 합니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영화관에서 판매하는 상품을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "상품 재고가 부족합니다."),
    PURCHASE_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "구매 가능한 금액 범위를 초과했습니다."),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "영화를 찾을 수 없습니다."),
    CINEMA_NOT_FOUND(HttpStatus.NOT_FOUND, "영화관을 찾을 수 없습니다."),
    MOVIE_KEEPING_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영화를 찜 한 적이 없습니다."),
    CINEMA_KEEPING_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영화관을 찜 한 적이 없습니다."),
    CINEMA_ALREADY_KEPT(HttpStatus.CONFLICT, "이미 찜한 영화관입니다."),
    MOVIE_ALREADY_KEPT(HttpStatus.CONFLICT, "이미 찜한 영화입니다."),
    SHOWTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "상영 일정을 찾을 수 없습니다."),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    SEAT_ALREADY_BOOKED(HttpStatus.CONFLICT, "이미 예매된 좌석입니다."),
    BOOKING_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 예약입니다."),
    SHOWTIME_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 상영이 시작되어 예매를 취소할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
