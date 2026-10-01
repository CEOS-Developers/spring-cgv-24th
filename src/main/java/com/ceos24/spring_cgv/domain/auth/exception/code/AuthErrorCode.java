package com.ceos24.spring_cgv.domain.auth.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH401_1", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_2", "인증 정보가 없거나 유효하지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED,"AUTH401_3" , "만료된 토큰입니다."),
    UNKNOWN_TOKEN_ERROR(HttpStatus.UNAUTHORIZED, "AUTH401_4","토큰 검증 중 오류가 발생했습니다." ),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED,"AUTH401_5" , "유효하지 않은 토큰입니다."),
    TOKEN_TYPE_MISMATCH(HttpStatus.UNAUTHORIZED,"AUTH401_6" ,"토큰의 타입 정보가 일치하지 않습니다." ),
    RT_COOKIE_MISSING(HttpStatus.UNAUTHORIZED,"AUTH401_7" ,"리프레시 토큰 쿠키가 존재하지 않습니다. 다시 로그인 해주세요." ),
    RT_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH401_8","만료되었거나 종료된 세션입니다. 다시 로그인해 주세요." ),
    RT_REUSE_DETECTED(HttpStatus.UNAUTHORIZED,"AUTH401_9" ,"과거의 리프레시 토큰입니다. 토큰 탈취를 감지하여 세션을 종료했습니다. 다시 로그인해 주세요." ),
    BLACKLISTED_TOKEN(HttpStatus.UNAUTHORIZED,"AUTH401_10" ,"로그아웃 처리된 토큰입니다. 다시 로그인해 주세요." ),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH409_1", "이미 가입된 이메일입니다."),

    AUTH_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,"AUTH503_1" ,"일시적으로 인증 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요." );

    private final HttpStatus status;
    private final String code;
    private final String message;
}