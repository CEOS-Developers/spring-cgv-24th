package com.ceos24.spring_cgv.domain.like.exception;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apipayload.exception.ProjectException;

public class CinemaLikeException extends ProjectException {
    public CinemaLikeException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}