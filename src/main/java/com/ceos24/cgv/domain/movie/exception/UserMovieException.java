package com.ceos24.cgv.domain.movie.exception;

import com.ceos24.cgv.global.exception.GeneralException;

public class UserMovieException extends GeneralException {

    public UserMovieException(
            UserMovieErrorCode errorCode
    ) {
        super(errorCode);
    }
}