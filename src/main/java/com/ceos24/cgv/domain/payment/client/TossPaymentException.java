package com.ceos24.cgv.domain.payment.client;

import lombok.Getter;

@Getter
public class TossPaymentException extends RuntimeException {

    public enum Reason {
        API_ERROR,
        COMMUNICATION_ERROR,
        INVALID_RESPONSE
    }

    private final Reason reason;
    private final Integer httpStatus;
    private final String errorCode;
    private final String providerMessage;

    private TossPaymentException(Reason reason, Integer httpStatus, String errorCode,
                                 String providerMessage, Throwable cause) {
        super("토스 통신 처리 오류: " + reason + " (HTTP " + httpStatus + ")", cause);
        this.reason = reason;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.providerMessage = providerMessage;
    }

    public static TossPaymentException apiError(int status, String code, String message) {
        return new TossPaymentException(Reason.API_ERROR, status, code, message, null);
    }

    public static TossPaymentException communicationError(Throwable cause) {
        return new TossPaymentException(Reason.COMMUNICATION_ERROR, null,
                "TOSS_COMMUNICATION_ERROR", null, cause);
    }

    public static TossPaymentException invalidResponse(Integer status, Throwable cause) {
        return new TossPaymentException(Reason.INVALID_RESPONSE, status,
                "TOSS_INVALID_RESPONSE", null, cause);
    }
}
