package com.ceos24.cgv.domain.movie.exception;

import com.ceos24.cgv.global.exception.GeneralException;

public class MovieException extends GeneralException {

    public MovieException(MovieErrorCode errorCode) {
        super(errorCode);
    }
}
