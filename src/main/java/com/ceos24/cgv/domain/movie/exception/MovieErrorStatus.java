package com.ceos24.cgv.domain.movie.exception;

import com.ceos24.cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.cgv.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MovieErrorStatus implements BaseErrorCode {

    MOVIE_NOT_FOUND(HttpStatus.NOT_FOUND, "MOVIE404", "존재하지 않는 영화입니다."),
    ALREADY_LIKED_MOVIE(HttpStatus.CONFLICT, "MOVIE409", "이미 찜한 영화입니다."),
    MOVIE_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "MOVIE404_1", "찜하지 않은 영화입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }
}
