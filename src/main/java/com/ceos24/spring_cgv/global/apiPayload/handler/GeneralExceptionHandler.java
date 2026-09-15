package com.ceos24.spring_cgv.global.apiPayload.handler;

import com.ceos24.spring_cgv.global.apiPayload.ApiResponse;
import com.ceos24.spring_cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apiPayload.code.GeneralErrorCode;
import com.ceos24.spring_cgv.global.apiPayload.exception.ProjectException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GeneralExceptionHandler {

    // 프로젝트 전용 예외 처리
    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectException(ProjectException e){

        BaseErrorCode errorCode = e.getErrorCode();
        log.warn("[프로젝트 지정 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, null));
    }

    // @ModelAttribute의 타입 변환 실패 혹은 검증 실패, @RequestBody의 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleNotValidException(MethodArgumentNotValidException e){

        // 타입 변환 or 검증 실패한 변수명과 실패 이유를 담을 Map
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach((error) -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        log.warn("[요청 DTO 지정 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // @PathVariable, @RequestParam (단일 파라미터)의 타입 변환 실패
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<String>> handleTypeMismatchException(MethodArgumentTypeMismatchException e){

        // 실패한 경로 변수 or 요청 파라미터를 담을 String
        String detail = e.getName() + " 타입이 올바르지 않습니다.";

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        log.warn("[요청 단일 파라미터 타입 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, detail));
    }

    // @PathVariable, @RequestParam (단일 파라미터)의 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String,String>>> handleValidationException(HandlerMethodValidationException e){

        // 검증 실패한 파라미터명과 실패 이유를 담을 Map
        Map<String, String> errors = new HashMap<>();
        e.getParameterValidationResults().forEach((error) -> {
            errors.put(error.getMethodParameter().getParameterName(), error.getResolvableErrors().getFirst().getDefaultMessage());
        });

        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        log.warn("[요청 단일 파라미터 검증 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // 그 외 지정되지 않은 예외
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleGlobalException(RuntimeException e){

        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        log.error("[미지정 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, e.getMessage()));
    }
}
