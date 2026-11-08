package com.ceos24.cgv.global.apiPayload.code;

import lombok.Getter;

@Getter
public enum ErrorCode {

    /**
     * ******************************* Global Error CodeList ***************************************
     * HTTP Status Code
     * 400 : Bad Request
     * 401 : Unauthorized
     * 403 : Forbidden
     * 404 : Not Found
     * 500 : Internal Server Error
     * *********************************************************************************************
     */
    // 잘못된 서버 요청
    BAD_REQUEST_ERROR(400, "G001", "Bad Request Exception"),

    // @RequestBody 데이터 미 존재
    INVALID_REQUEST_BODY(400, "G002", "요청 본문이 없거나 형식이 올바르지 않습니다."),

    // 유효하지 않은 타입
    INVALID_TYPE_VALUE(400, "G003", " Invalid Type Value"),

    // Request Parameter 로 데이터가 전달되지 않을 경우
    MISSING_REQUEST_PARAMETER_ERROR(400, "G004", "Missing Servlet RequestParameter Exception"),

    // 입력/출력 값이 유효하지 않음
    IO_ERROR(400, "G005", "I/O Exception"),

    // com.google.gson JSON 파싱 실패
    JSON_PARSE_ERROR(400, "G006", "JsonParseException"),

    // com.fasterxml.jackson.core Processing Error
    JACKSON_PROCESS_ERROR(400, "G007", "com.fasterxml.jackson.core Exception"),

    // 권한이 없음
    FORBIDDEN_ERROR(403, "G008", "Forbidden Exception"),

    // 서버로 요청한 리소스가 존재하지 않음
    NOT_FOUND_ERROR(404, "G009", "Not Found Exception"),

    // NULL Point Exception 발생
    NULL_POINT_ERROR(500, "G010", "Internal Server Error"),

    // @RequestBody 및 @RequestParam, @PathVariable 값이 유효하지 않음
    NOT_VALID_ERROR(400, "G011", "요청 값이 유효하지 않습니다."),

    // @RequestBody 및 @RequestParam, @PathVariable 값이 유효하지 않음
    NOT_VALID_HEADER_ERROR(400, "G012", "필수 요청 헤더가 없습니다."),

    METHOD_NOT_ALLOWED(405, "G013", "지원하지 않는 HTTP 메서드입니다."),

    UNSUPPORTED_MEDIA_TYPE(415, "G014", "지원하지 않는 Content-Type입니다."),

    // 서버가 처리 할 방법을 모르는 경우 발생
    INTERNAL_SERVER_ERROR(500, "G999", "Internal Server Error Exception"),


    /**
     * ********************************** 영화 관련 오류 **********************************************
     */
    MOVIE_NOT_FOUND(404, "M001", "존재하지 않는 영화입니다."),

    CINEMA_NOT_FOUND(404, "C001", "존재하지 않는 영화관입니다."),

    AUDITORIUM_TYPE_NOT_FOUND(404, "AT001", "존재하지 않는 상영관 타입입니다."),

    AUDITORIUM_NAME_DUPLICATED(409, "A001", "해당 영화관에 같은 이름의 상영관이 이미 존재합니다."),

    AUDITORIUM_NOT_FOUND(404, "A002", "존재하지 않는 상영관입니다."),

    INVALID_SCREENING_TIME(400, "S001", "상영 종료 시간은 시작 시간보다 뒤여야 합니다."),

    SCREENING_TIME_CONFLICT(409, "S002", "해당 상영관에 시간이 겹치는 상영 일정이 존재합니다."),

    LOGIN_FAILED(401, "AUTH001", "아이디 또는 비밀번호가 올바르지 않습니다."),

    TOKEN_EXPIRED(401, "AUTH002", "토큰이 만료되었습니다."),

    TOKEN_INVALID(401, "AUTH003", "유효하지 않은 토큰입니다."),

    TOKEN_NOT_EXIST(401, "AUTH004", "인증 토큰이 필요합니다."),

    ACCESS_DENIED(403, "AUTH005", "해당 요청에 접근할 권한이 없습니다."),

    USER_NOT_FOUND(404, "U001", "존재하지 않는 사용자입니다."),

    USER_ALREADY_EXISTS(409, "U002", "이미 가입된 사용자입니다."),

    MOVIE_ALREADY_FAVORITED(409, "MF001", "이미 찜한 영화입니다."),

    MOVIE_FAVORITE_NOT_FOUND(404, "MF002", "영화 찜 기록이 존재하지 않습니다."),

    CINEMA_ALREADY_FAVORITED(409, "CF001", "이미 찜한 영화관입니다."),

    CINEMA_FAVORITE_NOT_FOUND(404, "CF002", "영화관 찜 기록이 존재하지 않습니다."),

    SCREENING_NOT_FOUND(404, "S003", "존재하지 않는 상영정보입니다."),

    SEAT_NOT_FOUND(404, "SE001", "존재하지 않는 좌석이 포함되어 있습니다."),

    RESERVATION_NOT_FOUND(404, "R001", "존재하지 않는 예매입니다."),

    SEAT_NOT_IN_SCREENING_AUDITORIUM(400, "R002", "해당 상영 회차의 상영관 좌석이 아닙니다."),

    SEAT_ALREADY_RESERVED(409, "R003", "이미 예매된 좌석이 포함되어 있습니다."),

    DUPLICATE_SEAT_REQUEST(400, "R004", "중복된 좌석이 포함되어 있습니다."),

    RESERVATION_ALREADY_CANCELLED(409, "R005", "이미 취소된 예매입니다."),

    RESERVATION_ACCESS_DENIED(403, "R006", "해당 예매에 접근할 수 없습니다."),

    SCREENING_ALREADY_STARTED(409, "R007", "이미 시작된 상영 회차는 예매할 수 없습니다."),

    INVALID_RESERVATION_AMOUNT(400, "R008", "예매 금액은 0보다 커야 하며 지원하는 금액 범위여야 합니다."),
    RESERVATION_PAYMENT_CANCEL_REQUIRED(409, "R009", "결제된 예매는 결제 취소 API로 취소해주세요."),
    RESERVATION_PARTIAL_CANCEL_NOT_SUPPORTED(409, "R010", "영화 예매는 현재 전체 결제 취소만 지원합니다."),

    PRODUCT_NOT_FOUND(404, "P001", "존재하지 않는 상품이 포함되어 있습니다."),

    PRODUCT_NOT_AVAILABLE_AT_CINEMA(404, "P002", "해당 영화관에서 판매하지 않는 상품이 포함되어 있습니다."),

    INSUFFICIENT_STOCK(409, "P003", "상품 재고가 부족합니다."),

    DUPLICATE_PRODUCT_REQUEST(400, "P004", "중복된 상품이 포함되어 있습니다."),

    PURCHASE_NOT_FOUND(404, "P005", "존재하지 않는 구매 내역입니다."),

    PURCHASE_ACCESS_DENIED(403, "P006", "해당 구매 내역에 접근할 수 없습니다."),

    INVALID_PURCHASE_AMOUNT(400, "P007", "주문 금액은 0보다 커야 하며 지원하는 금액 범위를 초과할 수 없습니다."),

    PAYMENT_NOT_FOUND(404, "PAY001", "결제 정보가 존재하지 않습니다."),
    PAYMENT_ACCESS_DENIED(403, "PAY002", "본인의 결제만 처리할 수 있습니다."),
    PAYMENT_AMOUNT_MISMATCH(400, "PAY003", "주문 금액과 결제 금액이 일치하지 않습니다."),
    PAYMENT_ORDER_NOT_PAYABLE(409, "PAY004", "결제 가능한 주문이 아닙니다."),
    PAYMENT_ORDER_EXPIRED(409, "PAY005", "결제 대기 주문이 만료되었습니다."),
    PAYMENT_IDEMPOTENCY_CONFLICT(409, "PAY006", "같은 멱등키로 다른 요청을 처리할 수 없습니다."),
    PAYMENT_OPERATION_IN_PROGRESS(409, "PAY007", "처리 중이거나 확인이 필요한 결제 작업이 있습니다."),
    PAYMENT_OPERATION_FAILED(422, "PAY008", "결제 작업이 거절되었습니다. 작업 이력을 확인해주세요."),
    PAYMENT_RESULT_UNKNOWN(502, "PAY009", "결제 결과를 확인하지 못했습니다. 같은 멱등키로 다시 요청해주세요."),
    PAYMENT_INVALID_CANCEL_AMOUNT(400, "PAY010", "취소 금액이 남은 결제 금액 범위를 벗어났습니다."),
    PAYMENT_INVALID_IDEMPOTENCY_KEY(400, "PAY011", "멱등키는 제어 문자가 없는 300자 이하의 필수 값입니다."),
    PAYMENT_MANUAL_REVIEW_REQUIRED(409, "PAY012", "자동 처리를 계속할 수 없어 결제 내역 확인이 필요합니다."),
    PAYMENT_CHECKOUT_NOT_CONFIGURED(503, "PAY013", "결제 테스트 화면에는 API 개별 연동용 테스트 클라이언트 키와 시크릿 키가 필요합니다."),
    PAYMENT_TARGET_NOT_SUPPORTED(409, "PAY014", "아직 결제 처리가 연결되지 않은 주문 종류입니다.")

    ;

    /**
     * ******************************* Error Code Constructor ***************************************
     */
    // 에러 코드의 '코드 상태'을 반환한다.
    private final int status;

    // 에러 코드의 '코드간 구분 값'을 반환한다.
    private final String divisionCode;

    // 에러 코드의 '코드 메시지'을 반환한다.
    private final String message;

    // 생성자 구성
    ErrorCode(final int status, final String divisionCode, final String message) {
        this.status = status;
        this.divisionCode = divisionCode;
        this.message = message;
    }
}
