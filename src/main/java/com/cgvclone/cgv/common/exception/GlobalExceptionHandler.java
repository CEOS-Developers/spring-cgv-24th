package com.cgvclone.cgv.common.exception;

import com.cgvclone.cgv.common.logging.RequestLoggingFilter;
import com.cgvclone.cgv.common.logging.SafeExceptionLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(GlobalException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        request.setAttribute(RequestLoggingFilter.ERROR_CODE, errorCode.name());
        if (errorCode.getStatus().is5xxServerError()) {
            log.atError().addKeyValue("event", "application.rejected")
                    .addKeyValue("errorCode", errorCode.name()).log("Request could not be completed");
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        request.setAttribute(RequestLoggingFilter.ERROR_CODE, "INTERNAL_SERVER_ERROR");
        SafeExceptionLog.write(log, exception);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
