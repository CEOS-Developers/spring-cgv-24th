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
    INVALID_RESERVATION_REQUEST(HttpStatus.BAD_REQUEST, "상영회차, 좌석 및 관람객 인원이 올바르지 않습니다."),
    SCREENING_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 상영회차에서 선택할 수 있는 좌석을 찾을 수 없습니다."),
    RESOURCE_BUSY(HttpStatus.CONFLICT, "다른 요청이 동일한 데이터를 처리 중입니다. 잠시 후 다시 시도해 주세요."),
    SEAT_UNAVAILABLE(
            HttpStatus.CONFLICT,
            "이미 예매되었거나 다른 사용자가 선택 중인 좌석입니다."
    ),

    RESERVATION_EXPIRED(
            HttpStatus.CONFLICT,
            "좌석 점유 시간 10분이 만료되었습니다. 좌석을 다시 선택해 주세요."
    ),

    RESERVATION_CANCEL_DEADLINE_PASSED(
            HttpStatus.CONFLICT,
            "예매 취소 및 환불은 상영 시작 20분 전까지만 가능합니다."
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

    // Shop
    STORE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 매점 주문을 찾을 수 없습니다."),
    STORE_ORDER_EXPIRED(HttpStatus.CONFLICT, "매점 주문의 결제 대기 시간이 만료되었습니다."),
    STORE_ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "현재 상태에서는 매점 주문을 결제할 수 없습니다."),
    INVALID_STORE_ORDER_REQUEST(HttpStatus.BAD_REQUEST, "주문할 메뉴와 수량이 올바르지 않습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "매점 상품의 재고가 부족합니다."),
    STORE_INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영화관에서 판매하는 상품을 찾을 수 없습니다."),
    PAID_STORE_ORDER_CANCEL_NOT_SUPPORTED(HttpStatus.CONFLICT, "결제 완료 주문은 환불 처리 후 취소해야 합니다."),

    // Payment
    INVALID_PAYMENT_CANCEL_REQUEST(HttpStatus.BAD_REQUEST, "취소 사유(1~200자), 양수 금액 및 멱등키(소문자·숫자·_·-, 1~64자)를 확인해 주세요."),
    PAYMENT_NOT_CANCELABLE(HttpStatus.CONFLICT, "승인된 결제만 취소할 수 있습니다."),
    PAYMENT_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 전액 취소된 결제입니다."),
    PAYMENT_CANCEL_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "취소 금액이 남은 결제 금액을 초과합니다."),
    PAYMENT_CANCEL_IN_PROGRESS(HttpStatus.CONFLICT, "다른 취소 요청의 결과를 먼저 확인해야 합니다. 기존 요청의 멱등키로 재시도해 주세요."),
    PAYMENT_CANCEL_REQUEST_MISMATCH(HttpStatus.CONFLICT, "동일 멱등키에 다른 취소 내용을 사용할 수 없습니다."),
    PAYMENT_CANCEL_FAILED(HttpStatus.BAD_GATEWAY, "PG에서 결제 취소를 거절했습니다. 결제 금액은 변경되지 않았습니다."),
    PAYMENT_CANCEL_RESULT_UNKNOWN(HttpStatus.SERVICE_UNAVAILABLE, "취소 결과를 확인하지 못했습니다. 새 멱등키를 만들지 말고 동일한 요청으로 재시도해 주세요."),
    PAYMENT_CANCEL_METHOD_NOT_SUPPORTED(HttpStatus.CONFLICT, "가상계좌 환불은 환불 계좌 연동 후 사용할 수 있습니다."),
    INVALID_PAYMENT_PAGE(HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1 이상 100 이하로 입력해야 합니다."),

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

    RESERVATION_NOT_PAYABLE(
            HttpStatus.CONFLICT,
            "현재 상태에서는 해당 예매의 결제를 준비할 수 없습니다."
    ),

    PAYMENT_NOT_PREPARABLE(
            HttpStatus.CONFLICT,
            "기존 결제의 상태 또는 정보로 인해 결제를 준비할 수 없습니다."
    ),

    INVALID_PAYMENT_CONFIRM_REQUEST(
            HttpStatus.BAD_REQUEST,
            "결제 승인 요청값이 올바르지 않습니다."
    ),

    PAYMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 결제를 찾을 수 없습니다."
    ),

    PAYMENT_INFORMATION_MISMATCH(
            HttpStatus.CONFLICT,
            "저장된 결제 정보와 요청 정보가 일치하지 않습니다."
    ),

    INVALID_PAYMENT_STATUS(
            HttpStatus.CONFLICT,
            "현재 결제 상태에서는 요청을 처리할 수 없습니다."
    ),

    PAYMENT_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "결제 처리 중이므로 예매 또는 주문을 변경할 수 없습니다."
    ),

    PAYMENT_RESULT_UNKNOWN(
            HttpStatus.SERVICE_UNAVAILABLE,
            "결제 결과를 확인 중입니다. 같은 주문으로 다시 확인해 주세요."
    ),

    PAYMENT_APPROVAL_FAILED(
            HttpStatus.BAD_REQUEST,
            "결제가 실패하거나 만료되었습니다."
    ),

    PAID_RESERVATION_CANCEL_NOT_SUPPORTED(
            HttpStatus.CONFLICT,
            "결제 완료 예매는 환불 처리 후 취소해야 합니다."
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
