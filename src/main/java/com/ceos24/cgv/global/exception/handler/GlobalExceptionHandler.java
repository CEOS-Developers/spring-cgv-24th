package com.ceos24.cgv.global.exception.handler;


import com.ceos24.cgv.global.apiPayload.response.ErrorResponse;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;


/**
 * Controller 내에서 발생하는 Exception 대해서 Catch 하여 응답값(Response)을 보내주는 기능을 수행함.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();

        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    /**
     * [Exception] API 호출 시 '객체' 혹은 '파라미터' 데이터 값이 유효하지 않은 경우
     *
     * @param ex MethodArgumentNotValidException
     * @return ResponseEntity<ErrorResponse>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ErrorCode code = ErrorCode.NOT_VALID_ERROR;
        ErrorResponse response = ErrorResponse.of(code, ex.getBindingResult());
        return ResponseEntity.status(code.getStatus()).body(response);
    }

    /**
     * [Exception] API 호출 시 'Header' 내에 데이터 값이 유효하지 않은 경우
     *
     * @param ex MissingRequestHeaderException
     * @return ResponseEntity<ErrorResponse>
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    protected ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        ErrorCode code = ErrorCode.NOT_VALID_HEADER_ERROR;
        ErrorResponse response = ErrorResponse.of(code);
        return ResponseEntity.status(code.getStatus()).body(response);
    }

    /**
     * [Exception] 클라이언트에서 Body로 '객체' 데이터가 넘어오지 않았을 경우
     *
     * @param ex HttpMessageNotReadableException
     * @return ResponseEntity<ErrorResponse>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        ErrorCode code = ErrorCode.INVALID_REQUEST_BODY;
        ErrorResponse response = ErrorResponse.of(code);
        return ResponseEntity.status(code.getStatus()).body(response);
    }

    /**
     * [Exception] 클라이언트에서 request로 '파라미터로' 데이터가 넘어오지 않았을 경우
     *
     * @param ex MissingServletRequestParameterException
     * @return ResponseEntity<ErrorResponse>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ErrorResponse> handleMissingRequestHeaderExceptionException(MissingServletRequestParameterException ex) {
        ErrorCode code = ErrorCode.MISSING_REQUEST_PARAMETER_ERROR;
        ErrorResponse response = ErrorResponse.of(code);
        return ResponseEntity.status(code.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex
    ) {
        ErrorCode code = ErrorCode.INVALID_TYPE_VALUE;
        ErrorResponse response = ErrorResponse.of(code);

        return ResponseEntity.status(code.getStatus()).body(response);
    }


    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    protected ResponseEntity<ErrorResponse> handleNotFoundException(Exception ex) {
        ErrorCode code = ErrorCode.NOT_FOUND_ERROR;

        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex
    ) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;

        return ResponseEntity.status(code.getStatus())
                .headers(ex.getHeaders())
                .body(ErrorResponse.of(code));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex
    ) {
        ErrorCode code = ErrorCode.UNSUPPORTED_MEDIA_TYPE;

        return ResponseEntity.status(code.getStatus())
                .headers(ex.getHeaders())
                .body(ErrorResponse.of(code));
    }

    // ==================================================================================================================

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        log.error("Unexpected exception", ex);

        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code));
    }
}
