package com.ceos24.cgv.domain.reservation.exception;

import com.ceos24.cgv.global.exception.GeneralException;

public class ReservationException extends GeneralException {

    public ReservationException(
            ReservationErrorCode errorCode
    ) {
        super(errorCode);
    }
}