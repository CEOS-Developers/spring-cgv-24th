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

    USER_NOT_FOUND(404, "U001", "존재하지 않는 사용자입니다."),

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

    PRODUCT_NOT_FOUND(404, "P001", "존재하지 않는 상품이 포함되어 있습니다."),

    PRODUCT_NOT_AVAILABLE_AT_CINEMA(404, "P002", "해당 영화관에서 판매하지 않는 상품이 포함되어 있습니다."),

    INSUFFICIENT_STOCK(409, "P003", "상품 재고가 부족합니다."),

    DUPLICATE_PRODUCT_REQUEST(400, "P004", "중복된 상품이 포함되어 있습니다."),

    PURCHASE_NOT_FOUND(404, "P005", "존재하지 않는 구매 내역입니다."),

    PURCHASE_ACCESS_DENIED(403, "P006", "해당 구매 내역에 접근할 수 없습니다.")

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
