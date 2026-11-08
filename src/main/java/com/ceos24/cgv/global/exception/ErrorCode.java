package com.ceos24.cgv.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(400, "잘못된 입력값입니다."),
    FORBIDDEN_ERROR(409, "허용되지 않는 요청입니다."),

    // Member
    MEMBER_NOT_FOUND(404, "존재하지 않는 회원입니다."),

    // Auth
    AUTH_LOGIN_ID_ALREADY_EXISTS(409, "이미 존재하는 로그인 ID 입니다."),
    AUTH_EMAIL_ALREADY_EXISTS(409, "이미 사용중인 EMAIL입니다."),
    AUTH_INVALID_CREDENTIALS(401, "올바르지 않은 인증 정보입니다."),
    AUTH_UNAUTHORIZED_USER(401, "인증되지 않은 사용자입니다."),
    AUTH_FORBIDDEN_USER(403, "권한이 없는 사용자입니다."),
    AUTH_EXPIRED_TOKEN(401, "만료된 토큰입니다."),
    AUTH_INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
    AUTH_TOKEN_NOT_EXIST(401, "토큰이 존재하지 않습니다."),
    AUTH_HIJACK_DETECTED(401, "토큰 탈취가 의심됩니다."),

    // Store / Order
    STORE_NOT_FOUND(404, "존재하지 않는 매장입니다."),
    MENU_NOT_FOUND(404, "해당 매장에 존재하지 않는 메뉴입니다."),
    ORDER_OUT_OF_STOCK(400, "재고가 부족합니다."),
    ORDER_INVALID_QUANTITY(400, "주문 수량이 유효하지 않습니다."),
    ORDER_NOT_FOUND(404, "존재하지 않는 주문입니다."),
    ORDER_ITEM_NOT_FOUND(404, "존재하지 않는 주문 품목입니다."),
    ORDER_ITEM_CANCEL_QUANTITY_EXCEEDED(400, "취소 요청 수량이 남은 주문 수량을 초과했습니다."),

    // Reservation / Theater / FavoriteTheater
    ALREADY_RESERVED(409, "이미 예매되거나 결제 대기 중인 좌석입니다."),
    THEATER_NOT_FOUND(404, "존재하지 않는 영화관입니다."),
    RESERVATION_NOT_FOUND(404, "존재하지 않는 예매이거나 권한이 없습니다."),
    RESERVATION_SEAT_NOT_FOUND(404, "해당 예매 내역에 존재하지 않는 좌석입니다."),
    RESERVATION_EXPIRED(400, "예매 점유 시간이 만료되었습니다. 다시 좌석을 선택해 주세요."),
    RESERVATION_ALREADY_PROCESSED(400, "이미 확정되었거나 취소된 예매입니다."),
    RESERVATION_PAYMENT_CANCEL_REQUIRED(400, "결제가 완료된 예매는 결제 취소를 통해 취소해 주세요."),
    SEAT_NOT_FOUND(404, "존재하지 않는 좌석입니다."),
    SCREENING_NOT_FOUND(404, "존재하지 않는 상영 일정입니다."),
    SCREEN_NOT_FOUND(404, "존재하지 않는 상영관입니다."),
    FAVORITE_THEATER_ALREADY_EXIST(409, "이미 찜한 영화관입니다."),
    FAVORITE_THEATER_NOT_FOUND(404, "찜한 영화관이 존재하지 않습니다."),

    // Movie / FavoriteMovie
    MOVIE_NOT_FOUND(404, "존재하지 않는 영화입니다."),
    FAVORITE_MOVIE_ALREADY_EXIST(409, "이미 찜한 영화입니다."),
    FAVORITE_MOVIE_NOT_FOUND(404, "찜한 영화가 존재하지 않습니다."),

    // Payment
    PAYMENT_NOT_FOUND(404, "존재하지 않는 결제 정보입니다."),
    PAYMENT_IN_PROGRESS(409, "결제 승인이 진행 중입니다. 잠시 후 다시 확인해 주세요."),
    PAYMENT_AMOUNT_MISMATCH(400, "결제 요청 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_ALREADY_PROCESSED(400, "이미 완료되었거나 처리 불가능한 결제입니다."),
    PAYMENT_CANCEL_AMOUNT_EXCEEDED(400, "취소 요청 금액이 남은 결제 잔액을 초과했습니다."),
    PAYMENT_ALREADY_CANCELED(400, "이미 전액 취소된 결제입니다."),
    PAYMENT_GATEWAY_CLIENT_ERROR(400, "결제 대행사 승인 요청이 거절되었습니다."),
    PAYMENT_GATEWAY_TIMEOUT(504, "결제 대행사 통신 중 시간 초과가 발생했습니다."),
    PAYMENT_INVALID_RESPONSE(502, "결제 대행사의 응답 정보가 올바르지 않습니다."),
    PAYMENT_GATEWAY_ERROR(502, "외부 결제 대행사(PG) 통신 중 오류가 발생했습니다."),
    PAYMENT_COMPENSATION_FAILED(500, "시스템 오류 발생 후 자동 보상 취소 처리에 실패했습니다. 운영자 확인이 필요합니다."),
    WEBHOOK_INVALID_SIGNATURE(401, "유효하지 않은 웹훅 서명입니다.");

    private final int status;
    private final String message;
}
