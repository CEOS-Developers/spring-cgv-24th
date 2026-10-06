package com.ceos.cgv.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

@Slf4j
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

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.INVALID_REQUEST.name(),
                ErrorCode.INVALID_REQUEST.message()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) throws Exception {
        // Spring Security 예외는 보안 필터의 전용 처리 경로로 전달한다.
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            throw exception;
        }
        // 405, 415 등 프레임워크가 지정한 HTTP 상태와 헤더를 보존한다.
        if (exception instanceof org.springframework.web.ErrorResponse error) {
            HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                    .body(new ErrorResponse(error.getStatusCode().value(),
                            status == null ? "HTTP_ERROR" : status.name(),
                            status == null ? "요청을 처리할 수 없습니다." : status.getReasonPhrase()));
        }
        log.error("처리되지 않은 서버 오류", exception);
        ErrorCode error = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(error.httpStatus())
                .body(new ErrorResponse(error.httpStatus().value(), error.name(), error.message()));
    }
}
