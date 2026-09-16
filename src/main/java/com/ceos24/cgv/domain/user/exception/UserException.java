package com.ceos24.cgv.domain.user.exception;

import com.ceos24.cgv.global.exception.GeneralException;

public class UserException extends GeneralException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}