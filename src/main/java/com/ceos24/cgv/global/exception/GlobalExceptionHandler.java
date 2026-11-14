package com.ceos24.cgv.global.exception;

import com.ceos24.cgv.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        // 5xx 성격의 에러코드(결제 대행사 장애, 보상 취소 실패 등)는 운영자가 봐야 하므로 error + 스택트레이스
        if (errorCode.getStatus() >= 500) {
            log.error(
                    "business_exception code={} status={} message={}",
                    errorCode.name(),
                    errorCode.getStatus(),
                    e.getMessage(),
                    e);
        } else {
            log.warn(
                    "business_exception code={} status={} message={}",
                    errorCode.name(),
                    errorCode.getStatus(),
                    e.getMessage());
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(
                        errorCode.getStatus(), e.getMessage() != null ? e.getMessage() : errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());
        log.info("request_invalid type=MethodArgumentNotValid message={}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(400, errorMessage));
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("access_denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ErrorCode.AUTH_FORBIDDEN_USER.getStatus(), ErrorCode.AUTH_FORBIDDEN_USER.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        String message = "필수 요청 헤더가 누락되었습니다: " + e.getHeaderName();
        log.info("request_invalid type=MissingRequestHeader header={}", e.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        // 예외 메시지에 요청 본문 조각(비밀번호 등)이 섞일 수 있어 남기지 않는다
        log.info("request_invalid type=HttpMessageNotReadable");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(400, "요청 본문(JSON) 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        String message = "지원하지 않는 HTTP 메서드입니다: " + e.getMethod();
        log.info("request_invalid type=HttpRequestMethodNotSupported method={}", e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(405, message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException e) {
        // 예외 메시지에 SQL과 입력값(이메일 등)이 들어갈 수 있어 원인 예외 종류만 남긴다
        log.warn(
                "data_integrity_violation cause={}",
                e.getMostSpecificCause().getClass().getSimpleName());
        // 유니크 위반만 409(중복), 그 외(NOT NULL, FK, 길이 초과 등)는 요청 데이터 오류로 400
        if (e.getCause() instanceof ConstraintViolationException cve
                && cve.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, "이미 처리되었거나 중복된 데이터입니다."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "요청 데이터가 데이터베이스 제약 조건을 위반했습니다."));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    protected ResponseEntity<ApiResponse<Void>> handleLockingFailureException(Exception e) {
        log.warn("lock_conflict type={}", e.getClass().getSimpleName());
        ErrorCode code = ErrorCode.CONCURRENT_REQUEST_CONFLICT;
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code.getStatus(), code.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        String message = "필수 요청 파라미터가 누락되었습니다: " + e.getParameterName();
        log.info("request_invalid type=MissingServletRequestParameter parameter={}", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        String message = "요청 파라미터 형식이 올바르지 않습니다: " + e.getName();
        log.info("request_invalid type=MethodArgumentTypeMismatch parameter={}", e.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException e) {
        log.info("request_invalid type=HandlerMethodValidation");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {
        log.info("request_invalid type=HttpMediaTypeNotSupported contentType={}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(415, "지원하지 않는 Content-Type 입니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.info("request_invalid type=NoResourceFound");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("unhandled_exception type={}", e.getClass().getSimpleName(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "서버 내부에서 예상치 못한 에러가 발생했습니다."));
    }
}
