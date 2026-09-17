package com.cgvclone.cgv.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "영화를 찾을 수 없습니다."),
    CINEMA_NOT_FOUND(HttpStatus.NOT_FOUND, "영화관을 찾을 수 없습니다."),
    MOVIE_KEEPING_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영화를 찜 한 적이 없습니다."),
    CINEMA_KEEPING_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 영화관을 찜 한 적이 없습니다."),
    SHOWTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "상영 일정을 찾을 수 없습니다."),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    SEAT_ALREADY_BOOKED(HttpStatus.CONFLICT, "이미 예매된 좌석입니다."),
    BOOKING_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 예약입니다."),
    SHOWTIME_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 상영이 시작되어 예매를 취소할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
