package com.ceos24.spring_cgv.global.apipayload.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode{

    OK(HttpStatus.OK, "200_1", "성공적으로 요청을 처리했습니다."),
    CREATED(HttpStatus.CREATED, "201_1", "성공적으로 생성이 되었습니다.");
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
