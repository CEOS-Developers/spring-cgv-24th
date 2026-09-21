package com.ceos24.cgv.domain.theater.exception;

import com.ceos24.cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.cgv.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TheaterErrorStatus implements BaseErrorCode {

    THEATER_NOT_FOUND(HttpStatus.NOT_FOUND, "THEATER404", "존재하지 않는 영화관입니다."),
    ALREADY_LIKED_THEATER(HttpStatus.CONFLICT, "THEATER409", "이미 찜한 영화관입니다."),
    THEATER_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "THEATER404_1", "찜하지 않은 영화관입니다.");

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
