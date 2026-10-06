package com.ceos24.spring_cgv.domain.auth.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    LOGIN_OK(HttpStatus.OK, "AUTH200_1", "로그인이 성공적으로 완료되었습니다."),
    REISSUE_OK(HttpStatus.OK,"AUTH200_2" ,"토큰 재발급이 성공적으로 완료되었습니다." ),
    LOGOUT_OK(HttpStatus.OK, "AUTH200_3","로그아웃이 성공적으로 완료되었습니다." ),

    SIGNUP_OK(HttpStatus.CREATED, "AUTH201_1", "회원가입이 성공적으로 완료되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
