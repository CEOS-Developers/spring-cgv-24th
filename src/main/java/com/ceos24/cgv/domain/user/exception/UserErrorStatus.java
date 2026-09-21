package com.ceos24.cgv.domain.user.exception;

import com.ceos24.cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.cgv.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorStatus implements BaseErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "존재하지 않는 유저입니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "USER409", "이미 사용중인 아이디입니다.");

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
