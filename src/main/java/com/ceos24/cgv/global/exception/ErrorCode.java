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
    PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "구매 정보를 찾을 수 없습니다."),

    // 인증
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_NOT_EXIST(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    // 없음·만료·폐기를 나누지 않는다. 어느 경우든 클라이언트가 할 일은 다시 로그인하는 것 하나뿐이다.
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다. 다시 로그인해 주세요."),
    // 재로그인에 더해 탈취 가능성을 사용자에게 알려야 하는 경우라 일반 거부와 나눈다.
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED,
            "이미 사용된 리프레시 토큰입니다. 보안을 위해 이 로그인의 모든 토큰을 폐기했습니다. 다시 로그인해 주세요."),

    // 예매
    SEAT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "좌석이 상영관 범위를 벗어났습니다."),
    DUPLICATE_SEAT_IN_REQUEST(HttpStatus.BAD_REQUEST, "요청 안에 중복된 좌석이 있습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 선택된 좌석입니다."),
    SEAT_RESERVATION_CONFLICT(HttpStatus.CONFLICT, "좌석 선점 경합이 발생했습니다. 다시 시도해 주세요."),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 예매입니다."),
    RESERVATION_NOT_PENDING(HttpStatus.CONFLICT, "결제 대기 상태인 예매가 아닙니다."),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "선점 시간이 만료된 예매입니다."),
    CANCEL_DEADLINE_PASSED(HttpStatus.CONFLICT, "상영 20분 전까지만 취소할 수 있습니다."),
    RESERVATION_NOT_RESERVED(HttpStatus.CONFLICT, "결제된 예매만 일부 좌석을 취소할 수 있습니다."),
    SEAT_NOT_IN_RESERVATION(HttpStatus.BAD_REQUEST, "이 예매의 좌석이 아닙니다."),
    SEAT_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 좌석입니다."),

    // 결제
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 다릅니다."),
    PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 결제가 있습니다. 잠시 후 결제 결과를 확인해 주세요."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "현재 상태에서 처리할 수 없는 결제입니다. 결제를 다시 시작해 주세요."),
    PAYMENT_REJECTED(HttpStatus.PAYMENT_REQUIRED, "결제가 승인되지 않았습니다. 다른 결제수단으로 다시 시도해 주세요."),
    // 토스가 승인했는지 모르는 상태다. 실패로 안내하면 이미 돈이 빠진 회원이 다시 결제할 수 있다.
    PAYMENT_RESULT_UNKNOWN(HttpStatus.GATEWAY_TIMEOUT, "결제 결과를 확인하지 못했습니다. 잠시 후 내역에서 결제 여부를 확인해 주세요."),

    // 결제 취소
    PAYMENT_NOT_CANCELABLE(HttpStatus.CONFLICT, "승인된 결제만 취소할 수 있습니다."),
    PAYMENT_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 전액 취소된 결제입니다."),
    CANCEL_AMOUNT_EXCEEDS_BALANCE(HttpStatus.CONFLICT, "취소 금액이 남은 결제 금액보다 큽니다."),
    PAYMENT_CANCEL_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 취소가 있습니다. 잠시 후 결제 내역을 확인해 주세요."),
    // 토스 쪽 결제 상태 때문에 취소할 수 없다는 뜻이다. 토스는 정상 응답을 줬으므로 502가 아니다.
    PAYMENT_CANCEL_REJECTED(HttpStatus.CONFLICT, "결제 취소가 거절되었습니다. 고객센터로 문의해 주세요."),
    // 환불됐는지 모르는 상태다. 좌석·재고는 놓지 않고, 같은 취소를 다시 보내 결과를 맞춘다.
    PAYMENT_CANCEL_RESULT_UNKNOWN(HttpStatus.GATEWAY_TIMEOUT, "취소 결과를 확인하지 못했습니다. 잠시 후 취소를 다시 시도해 주세요."),
    PAYMENT_CANCEL_NOT_FOUND(HttpStatus.NOT_FOUND, "취소 요청을 찾을 수 없습니다."),
    PAYMENT_CANCEL_NOT_RETRYABLE(HttpStatus.CONFLICT, "이미 결과가 정해진 취소 요청입니다."),
    PAYMENT_CANCEL_RETRY_EXPIRED(HttpStatus.CONFLICT, "재시도 기한이 지난 취소 요청입니다. 고객센터로 문의해 주세요."),

    // 찜
    LIKE_REQUEST_CONFLICT(HttpStatus.CONFLICT, "찜 요청이 동시에 처리되었습니다. 다시 시도해 주세요."),

    // 매점
    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "재고는 1개 이상이어야 합니다."),
    DUPLICATE_PRODUCT_IN_REQUEST(HttpStatus.BAD_REQUEST, "요청 안에 중복된 상품이 있습니다."),
    BRANCH_NOT_OPERATING(HttpStatus.CONFLICT, "운영 중인 지점이 아닙니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    STOCK_LOCK_CONFLICT(HttpStatus.CONFLICT, "주문이 몰려 처리하지 못했습니다. 다시 시도해 주세요."),
    PURCHASE_NOT_PENDING(HttpStatus.CONFLICT, "결제 대기 상태인 주문이 아닙니다. 다시 결제하려면 새로 주문해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
