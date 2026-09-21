package com.ceos24.spring_cgv.domain.movie.exception;

import com.ceos24.spring_cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apiPayload.exception.ProjectException;

public class CinemaException extends ProjectException {
    public CinemaException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
