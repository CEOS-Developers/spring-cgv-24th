package com.ceos24.spring_cgv.domain.reservation.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements BaseErrorCode {

    RESERVATION_SEAT_EMPTY(HttpStatus.INTERNAL_SERVER_ERROR, "RESERVATION500_1", "예매할 좌석 정보가 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
