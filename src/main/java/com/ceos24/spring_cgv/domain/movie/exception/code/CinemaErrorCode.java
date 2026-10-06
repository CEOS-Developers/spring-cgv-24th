package com.ceos24.spring_cgv.domain.movie.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CinemaErrorCode implements BaseErrorCode {

    CINEMA_NOT_FOUND(HttpStatus.NOT_FOUND, "CINEMA404_1", "해당 영화관을 찾을 수 없습니다."),
    CINEMA_ALREADY_EXISTS(HttpStatus.CONFLICT, "CINEMA409_1", "같은 지역과 주소를 가진 영화관이 이미 존재합니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}