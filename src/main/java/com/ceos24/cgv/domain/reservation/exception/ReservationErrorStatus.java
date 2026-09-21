package com.ceos24.cgv.domain.reservation.exception;

import com.ceos24.cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.cgv.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ReservationErrorStatus implements BaseErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION404", "존재하지 않는 예매입니다."),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION404_1", "존재하지 않는 좌석입니다."),
    SEAT_NOT_IN_SCREEN(HttpStatus.BAD_REQUEST, "RESERVATION400", "해당 상영관의 좌석이 아닙니다."),
    DUPLICATE_SEAT_REQUEST(HttpStatus.BAD_REQUEST, "RESERVATION400_1", "중복된 좌석이 포함되어 있습니다."),
    RESERVATION_CLOSED(HttpStatus.BAD_REQUEST, "RESERVATION400_2", "이미 상영이 시작되어 예매할 수 없습니다."),
    CANCEL_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "RESERVATION400_3", "상영 시작 20분 전까지만 취소할 수 있습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "RESERVATION409", "이미 예매된 좌석이 포함되어 있습니다."),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "RESERVATION409_1", "이미 취소된 예매입니다."),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "RESERVATION403", "본인의 예매만 조회하거나 취소할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder().message(message).code(code).isSuccess(false).build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder().message(message).code(code).isSuccess(false).httpStatus(httpStatus).build();
    }
}
