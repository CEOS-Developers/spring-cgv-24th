package com.ceos24.cgv.domain.reservation.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ReservationErrorCode implements BaseErrorCode {

    RESERVATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RESERVATION4041",
            "존재하지 않는 예매입니다."
    ),

    SCREENING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RESERVATION4042",
            "존재하지 않는 상영 일정입니다."
    ),

    SEAT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RESERVATION4043",
            "존재하지 않는 좌석이 포함되어 있습니다."
    ),

    EMPTY_SEAT_LIST(
            HttpStatus.BAD_REQUEST,
            "RESERVATION4001",
            "예매할 좌석을 선택해야 합니다."
    ),

    INVALID_SEAT(
            HttpStatus.BAD_REQUEST,
            "RESERVATION4002",
            "해당 상영관에 속하지 않는 좌석입니다."
    ),

    DUPLICATE_SEAT(
            HttpStatus.BAD_REQUEST,
            "RESERVATION4003",
            "동일한 좌석이 중복으로 요청되었습니다."
    ),

    SEAT_ALREADY_RESERVED(
            HttpStatus.CONFLICT,
            "RESERVATION4091",
            "이미 예매된 좌석이 포함되어 있습니다."
    ),

    RESERVATION_ALREADY_CANCELED(
            HttpStatus.CONFLICT,
            "RESERVATION4092",
            "이미 취소된 예매입니다."
    ),

    SCREENING_ALREADY_STARTED(
            HttpStatus.CONFLICT,
            "RESERVATION4093",
            "이미 시작된 상영 일정은 예매할 수 없습니다."
    ),

    RESERVATION_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "RESERVATION4031",
            "해당 예매를 취소할 권한이 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}