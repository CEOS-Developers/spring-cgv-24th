package com.ceos24.cgv.domain.payment.exception;

import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class TossPaymentException extends BusinessException {

    private final boolean clientError;
    private final boolean serverError;
    private final boolean timeout;
    private final String tossCode;

    private TossPaymentException(
            ErrorCode errorCode,
            String message,
            boolean clientError,
            boolean serverError,
            boolean timeout,
            String tossCode) {
        super(errorCode, message);
        this.clientError = clientError;
        this.serverError = serverError;
        this.timeout = timeout;
        this.tossCode = tossCode;
    }

    public static TossPaymentException clientError(String tossCode, String message) {
        String detailMessage = message != null && !message.isBlank() ? message : "결제 요청이 거절되었습니다.";
        return new TossPaymentException(
                ErrorCode.PAYMENT_GATEWAY_CLIENT_ERROR, detailMessage, true, false, false, tossCode);
    }

    public static TossPaymentException serverError(String tossCode, String message) {
        String detailMessage = message != null && !message.isBlank() ? message : "토스페이먼츠 서버에 일시적인 장애가 발생했습니다.";
        return new TossPaymentException(ErrorCode.PAYMENT_GATEWAY_ERROR, detailMessage, false, true, false, tossCode);
    }

    public static TossPaymentException timeout(String message, Throwable cause) {
        String detailMessage = message != null && !message.isBlank() ? message : "토스 결제 대행사 통신 중 시간 초과가 발생했습니다.";
        TossPaymentException exception = new TossPaymentException(
                ErrorCode.PAYMENT_GATEWAY_TIMEOUT, detailMessage, false, false, true, "TIMEOUT");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
