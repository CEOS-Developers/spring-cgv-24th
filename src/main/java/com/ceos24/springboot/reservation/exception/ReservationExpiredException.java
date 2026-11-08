package com.ceos24.springboot.reservation.exception;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;

public class ReservationExpiredException extends GlobalException {
    public ReservationExpiredException() {
        super(ErrorCode.RESERVATION_EXPIRED);
    }
}
