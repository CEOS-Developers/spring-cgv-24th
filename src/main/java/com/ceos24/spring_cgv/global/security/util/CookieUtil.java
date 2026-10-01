package com.ceos24.spring_cgv.global.security.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    @Value("${jwt.rt-validity}") private long rtValidityMillis;

    /***
     * 함수 기능: RT를 담은 쿠키를 응답 헤더에 추가한다.
     * @param response 쿠키를 실을 응답 객체
     * @param refreshToken 쿠키에 담을 RT 원문
     */
    public void setRtCookie(HttpServletResponse response, String refreshToken){

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth/reissue")
                .maxAge(Duration.ofMillis(rtValidityMillis))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /***
     * 함수 기능: RT 쿠키를 즉시 만료시킨다.
     * @param response 쿠키에 실을 응답 객체
     */
    public void expireRtCookie(HttpServletResponse response){

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth/reissue")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
