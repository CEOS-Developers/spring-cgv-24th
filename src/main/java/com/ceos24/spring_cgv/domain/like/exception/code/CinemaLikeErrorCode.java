package com.ceos24.spring_cgv.domain.like.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CinemaLikeErrorCode implements BaseErrorCode {

    CINEMA_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "CINEMALIKE404_1", "찜하지 않은 영화관입니다."),
    CINEMA_LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT, "CINEMALIKE409_1", "이미 찜한 영화관입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}