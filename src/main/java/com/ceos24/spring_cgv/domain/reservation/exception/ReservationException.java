package com.ceos24.spring_cgv.domain.reservation.exception;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apipayload.exception.ProjectException;

public class ReservationException extends ProjectException {
    public ReservationException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
