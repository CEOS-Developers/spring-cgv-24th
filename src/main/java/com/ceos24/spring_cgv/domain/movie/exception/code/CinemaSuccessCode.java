package com.ceos24.spring_cgv.domain.movie.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CinemaSuccessCode implements BaseSuccessCode {

    CINEMA_CREATED(HttpStatus.CREATED, "CINEMA201_1", "영화관이 성공적으로 등록되었습니다."),
    CINEMA_LIST_FETCHED(HttpStatus.OK, "CINEMA200_1", "영화관 목록을 성공적으로 조회했습니다."),
    CINEMA_FETCHED(HttpStatus.OK, "CINEMA200_2", "영화관을 성공적으로 조회했습니다."),
    CINEMA_UPDATED(HttpStatus.OK, "CINEMA200_3", "영화관 정보가 성공적으로 수정되었습니다."),
    CINEMA_DELETED(HttpStatus.OK, "CINEMA200_4", "영화관이 성공적으로 삭제되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}