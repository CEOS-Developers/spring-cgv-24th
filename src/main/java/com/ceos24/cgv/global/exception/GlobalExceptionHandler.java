package com.ceos24.cgv.global.exception;

import com.ceos24.cgv.global.response.ApiResponse;
import com.ceos24.cgv.global.response.ApiResponse.FieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 좌석 경합 409·404 같은 4xx는 정상 흐름이라 경고로 남기면 진짜 경고가 묻힌다. 건수는 http.server.requests 지표가 센다.
    // 5xx 계열(토스 장애·결과 불명)만 사람이 봐야 하는 일이다.
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustom(CustomException e) {
        ErrorCode code = e.getErrorCode();
        if (code.getHttpStatus().is5xxServerError()) {
            log.warn("[CustomException] {}", code.name());
        } else {
            log.debug("[CustomException] {}", code.name());
        }
        return ResponseEntity.status(code.getHttpStatus())
                             .body(ApiResponse.error(code));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        return invalidInput(e.getBindingResult().getFieldErrors().stream()
                .map(fe -> FieldError.of(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
                .toList());
    }

    // enum 쿼리 파라미터에 없는 값이 들어오면 바인딩 단계에서 터진다. 400이어야 할 오류다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.debug("[TypeMismatch] {}={}", e.getName(), e.getValue());
        return invalidInput(List.of(FieldError.of(e.getName(), e.getValue(), "허용되지 않은 값입니다.")));
    }

    // 필수 쿼리 파라미터 누락. 처리하지 않으면 아래 Exception 핸들러로 떨어져 500이 된다.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.debug("[MissingParam] {}", e.getParameterName());
        return invalidInput(List.of(FieldError.of(e.getParameterName(), null, "필수 파라미터입니다.")));
    }

    // JSON 문법 오류, 날짜 형식 오류, enum에 없는 값처럼 본문을 객체로 바꾸지 못한 경우.
    // 처리하지 않으면 아래 Exception 핸들러로 떨어져 500이 된다.
    // 메시지에는 파서가 읽던 본문 조각(비밀번호 등)이 섞여 나오므로 원인 예외의 종류만 남긴다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.debug("[NotReadable] {}", e.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.badRequest()
                             .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[Unhandled] ", e);
        return ResponseEntity.internalServerError()
                             .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> invalidInput(List<FieldError> fieldErrors) {
        return ResponseEntity.badRequest()
                             .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, fieldErrors));
    }
}
