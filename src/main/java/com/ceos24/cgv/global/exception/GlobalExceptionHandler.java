package com.ceos24.cgv.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[CustomException] {}: {}", code.name(), e.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                             .body(ErrorResponse.of(code));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null,
                        fe.getDefaultMessage()
                ))
                .toList();
        return ResponseEntity.badRequest()
                             .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[Unhandled] ", e);
        return ResponseEntity.internalServerError()
                             .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
