package com.ceos24.cgv.domain.theater.exception;

import com.ceos24.cgv.domain.movie.exception.MovieErrorCode;
import com.ceos24.cgv.global.exception.GeneralException;

public class TheaterException extends GeneralException {

    public TheaterException(TheaterErrorCode errorCode) {
        super(errorCode);
    }
}