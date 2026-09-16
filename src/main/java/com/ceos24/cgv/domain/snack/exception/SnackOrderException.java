package com.ceos24.cgv.domain.snack.exception;

import com.ceos24.cgv.global.exception.GeneralException;

public class SnackOrderException extends GeneralException {

    public SnackOrderException(
            SnackOrderErrorCode errorCode
    ) {
        super(errorCode);
    }
}