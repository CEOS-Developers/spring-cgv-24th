package com.ceos24.spring_cgv.domain.like.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CinemaLikeSuccessCode implements BaseSuccessCode {

    CINEMA_LIKE_CREATED(HttpStatus.CREATED, "CINEMALIKE201_1", "영화관을 찜 목록에 추가했습니다."),
    CINEMA_LIKE_DELETED(HttpStatus.OK, "CINEMALIKE200_1", "영화관을 찜 목록에서 제거했습니다."),
    CINEMA_LIKE_LIST_FETCHED(HttpStatus.OK, "CINEMALIKE200_2", "찜한 영화관 목록을 성공적으로 조회했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}