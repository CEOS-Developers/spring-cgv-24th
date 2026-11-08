package com.spring_cgv_24th.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),

    // 영화관
    THEATER_NOT_FOUND(HttpStatus.NOT_FOUND, "THEATER404", "영화관을 찾을 수 없습니다."),

    // 영화
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "MOVIE404", "영화를 찾을 수 없습니다."),
    INVALID_MOVIE_DURATION(HttpStatus.INTERNAL_SERVER_ERROR, "MOVIE_DURATION500",
            "저장된 영화 상영 시간이 올바르지 않습니다."),

    // 상영관
    AUDITORIUM_NOT_FOUND(HttpStatus.NOT_FOUND, "AUDITORIUM404", "상영관을 찾을 수 없습니다."),
    AUDITORIUM_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "AUDITORIUM_TYPE404", "상영관 유형을 찾을 수 없습니다."),
    INVALID_AUDITORIUM_CONFIG(HttpStatus.INTERNAL_SERVER_ERROR, "AUDITORIUM_CONFIG500",
            "저장된 상영관 좌석 규격이 올바르지 않습니다."),
    SCREENING_NOT_FOUND(HttpStatus.NOT_FOUND, "SCREENING404", "상영 회차를 찾을 수 없습니다."),
    SCREENING_OVERLAP(HttpStatus.CONFLICT, "SCREENING409", "상영관의 다른 회차와 시간이 겹칩니다."),
    TICKET_PRICE_CONFIG_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "TICKET_PRICE_CONFIG500",
            "영화표 가격 설정이 누락되었거나 올바르지 않습니다."),
    SCREENING_ALREADY_STARTED(HttpStatus.CONFLICT, "SCREENING_STARTED409", "이미 시작된 상영 회차는 예매할 수 없습니다."),

    // 유저
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404", "회원을 찾을 수 없습니다."),
    MEMBER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMBER_EMAIL409", "이미 사용 중인 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401", "이메일 또는 비밀번호가 올바르지 않습니다."),

    // 인증/인가 응답 코드도 다른 도메인과 동일하게 오류 식별자 뒤에 HTTP 상태 번호를 붙인다.
    TOKEN_NOT_EXIST(HttpStatus.UNAUTHORIZED, "TOKEN_NOT_EXIST401", "토큰이 없습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED401", "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID401", "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID401", "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED401", "Refresh Token이 만료되었습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED403", "접근 권한이 없습니다."),

    // 예매
    SCREENING_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "SEAT404", "해당 회차의 좌석을 찾을 수 없습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "SEAT409", "이미 예매된 좌석입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION404", "예매를 찾을 수 없습니다."),
    RESERVATION_FORBIDDEN(HttpStatus.FORBIDDEN, "RESERVATION403", "본인의 예매만 취소할 수 있습니다."),
    RESERVATION_ALREADY_CANCELLED(HttpStatus.CONFLICT, "RESERVATION409", "이미 취소된 예매입니다."),
    RESERVATION_CANCELLATION_CLOSED(HttpStatus.CONFLICT, "RESERVATION_CLOSED409", "상영 시작 후에는 예매를 취소할 수 없습니다."),

    // 영화관 찜
    THEATER_FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "THEATER_FAVORITE404", "영화관 찜을 찾을 수 없습니다."),
    THEATER_FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "THEATER_FAVORITE409", "이미 찜한 영화관입니다."),

    // 영화 찜
    MOVIE_FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "MOVIE_FAVORITE404", "영화 찜을 찾을 수 없습니다."),
    MOVIE_FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "MOVIE_FAVORITE409", "이미 찜한 영화입니다."),

    // 매점
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT404", "매점 상품을 찾을 수 없습니다."),
    THEATER_STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "THEATER_STOCK404", "영화관의 상품 재고를 찾을 수 없습니다."),
    STORE_STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "STORE_STOCK409", "구매 가능한 재고가 부족합니다."),

    // 결제
    PAYMENT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_TARGET404", "결제 대상을 찾을 수 없습니다."),
    PAYMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "PAYMENT403", "본인의 결제만 처리하거나 조회할 수 있습니다."),
    PAYMENT_TARGET_NOT_PAYABLE(HttpStatus.CONFLICT, "PAYMENT_TARGET409", "결제할 수 없는 상태이거나 기한이 지났습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_NOT_CANCELLABLE(HttpStatus.CONFLICT, "PAYMENT_CANCEL409", "승인 완료된 결제만 취소할 수 있습니다."),
    PAYMENT_CANCEL_INVALID(HttpStatus.BAD_REQUEST, "PAYMENT_CANCEL400", "취소할 상품·수량 또는 좌석을 확인해주세요."),
    PAYMENT_CANCEL_REQUEST_CONFLICT(HttpStatus.CONFLICT, "PAYMENT_CANCEL_REQUEST409", "같은 취소 요청 ID에 다른 취소 내용을 사용할 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT400", "결제 금액이 일치하지 않습니다."),
    PAYMENT_REQUEST_CONFLICT(HttpStatus.CONFLICT, "PAYMENT_REQUEST409", "주문번호 또는 결제 키가 일치하지 않습니다."),
    PAYMENT_PENDING(HttpStatus.CONFLICT, "PAYMENT_PENDING409", "결제 조회로 처리 결과를 확인해주세요."),
    PAYMENT_CONFIRMATION_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED400", "결제 승인이 거절되었습니다."),
    PAYMENT_CANCELLATION_REJECTED(HttpStatus.BAD_REQUEST, "PAYMENT_CANCEL_REJECTED400", "결제 취소가 거절되었습니다."),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY502", "결제사 응답을 확인할 수 없습니다. 결제 조회로 상태를 확인해주세요."),
    RESERVATION_PAYMENT_CANCELLATION_REQUIRED(HttpStatus.CONFLICT, "RESERVATION_PAYMENT409",
            "결제 진행 중인 예매는 결과 확인이 필요하며, 결제 완료 예매는 환불 절차가 필요합니다.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
