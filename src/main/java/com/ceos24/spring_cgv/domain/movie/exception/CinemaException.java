package com.ceos24.spring_cgv.domain.movie.exception;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apipayload.exception.ProjectException;

public class CinemaException extends ProjectException {
    public CinemaException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
