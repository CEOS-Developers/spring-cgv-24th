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

    // 예매
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404", "회원을 찾을 수 없습니다."),
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
    STORE_STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "STORE_STOCK409", "구매 가능한 재고가 부족합니다.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
