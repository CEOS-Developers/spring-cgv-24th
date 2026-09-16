package com.ceos24.cgv.domain.movie.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MovieErrorCode implements BaseErrorCode {

    MOVIE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MOVIE404",
            "존재하지 않는 영화입니다."
    );
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
