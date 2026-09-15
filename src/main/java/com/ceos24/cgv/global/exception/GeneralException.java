package com.ceos24.cgv.global.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.Getter;

@Getter
//비즈니스 로직에서 오류가 발생했음을 알리는 역할
public class GeneralException extends RuntimeException{

    //예외에서 발생한 상세 내용
    private final BaseErrorCode errorCode;

    //생성자
    public GeneralException(BaseErrorCode code) {
        this.errorCode = code;
    }

    public GeneralException(BaseErrorCode code, String message) {
        super(message);
        this.errorCode = code;
    }


}
