package com.ceos.cgv.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.httpStatus())
                .body(new ErrorResponse(
                        errorCode.httpStatus().value(),
                        errorCode.name(),
                        errorCode.message()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.INVALID_REQUEST.name(),
                exception.getMessage()
        ));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.INVALID_REQUEST.name(),
                ErrorCode.INVALID_REQUEST.message()
        ));
    }
}
