package com.ceos24.cgv.domain.theater.exception;

import com.ceos24.cgv.global.exception.GeneralException;

public class UserTheaterException extends GeneralException {

    public UserTheaterException(
            UserTheaterErrorCode errorCode
    ) {
        super(errorCode);
    }
}