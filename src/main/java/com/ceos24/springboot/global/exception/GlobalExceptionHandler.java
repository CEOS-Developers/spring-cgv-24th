package com.ceos24.springboot.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


// Controller에서 예외가 발생하면 여기서 공통으로 처리!!
@RestControllerAdvice
public class GlobalExceptionHandler {

//    IllegalArgumentException이 발생하면 이 메서드를 실행함.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e
    ) {

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
}
