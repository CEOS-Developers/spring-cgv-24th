package com.ceos24.cgv.global.exception;

import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.ErrorReasonDTO;
import com.ceos24.cgv.global.apiPayload.code.status.GlobalErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 커스텀 비즈니스 예외
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(GeneralException e, HttpServletRequest request) {
        ErrorReasonDTO errorReason = e.getErrorReasonHttpStatus();
        log.warn("비즈니스 예외 발생 - URI: {}, Code: {}", request.getRequestURI(), errorReason.getCode());

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), errorReason.getMessage(), null));
    }

    // 2. @Valid DTO 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        Map<String, String> errors = extractFieldErrors(e.getBindingResult().getFieldErrors());
        ErrorReasonDTO errorReason = GlobalErrorStatus._BAD_REQUEST.getReasonHttpStatus();

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), errorReason.getMessage(), errors));
    }

    // 3-1. @PathVariable, @RequestParam 단일 파라미터 검증 실패 (구버전 흐름)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );

        ErrorReasonDTO errorReason = GlobalErrorStatus._BAD_REQUEST.getReasonHttpStatus();

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), errorReason.getMessage(), errors));
    }

    // 3-2. @PathVariable, @RequestParam 검증 실패 (Spring 6.1+ 신버전 흐름)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        e.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String key = (parameterName != null) ? parameterName : "parameter";

            result.getResolvableErrors().forEach(error -> {

                String errorMessage = Optional.ofNullable(error.getDefaultMessage())
                        .orElse("유효하지 않은 값입니다.");
                errors.merge(key, errorMessage, (existing, newMsg) -> existing + ", " + newMsg);
            });
        });

        ErrorReasonDTO errorReason = GlobalErrorStatus._BAD_REQUEST.getReasonHttpStatus();

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), errorReason.getMessage(), errors));
    }

    // 4. 요청 본문(JSON) 파싱 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {

        ErrorReasonDTO errorReason = GlobalErrorStatus._BAD_REQUEST.getReasonHttpStatus();

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), "요청 본문 형식이 올바르지 않습니다.", null));
    }

    // 5. @PathVariable/@RequestParam 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        ErrorReasonDTO errorReason = GlobalErrorStatus._BAD_REQUEST.getReasonHttpStatus();
        String message = String.format("'%s' 파라미터의 값이 올바르지 않습니다.", e.getName());

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), message, null));
    }

    // 6. 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {

        ErrorReasonDTO errorReason = GlobalErrorStatus._BAD_REQUEST.getReasonHttpStatus();
        String message = String.format("필수 파라미터 '%s'가 누락되었습니다.", e.getParameterName());

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), message, null));
    }

    // 7. DB 제약 위반 (동시 요청으로 인한 중복 삽입, FK 참조 중인 데이터 삭제 시도 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolationException(
            DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("데이터 무결성 제약 위반 - URI: {}", request.getRequestURI(), e);

        ErrorReasonDTO errorReason = GlobalErrorStatus._CONFLICT.getReasonHttpStatus();

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), errorReason.getMessage(), null));
    }

    // 8. 그 외 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e, HttpServletRequest request) {
        log.error("서버 내부 오류 발생 - URI: {}", request.getRequestURI(), e);

        ErrorReasonDTO errorReason = GlobalErrorStatus._INTERNAL_SERVER_ERROR.getReasonHttpStatus();

        return ResponseEntity
                .status(errorReason.getHttpStatus())
                .body(ApiResponse.onFailure(errorReason.getCode(), errorReason.getMessage(), null));
    }

    private Map<String, String> extractFieldErrors(java.util.List<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : fieldErrors) {
            String fieldName = fieldError.getField();
            String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("잘못된 입력입니다.");
            errors.merge(fieldName, errorMessage, (existing, newMsg) -> existing + ", " + newMsg);
        }
        return errors;
    }
}
