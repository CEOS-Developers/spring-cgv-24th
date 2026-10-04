package com.ceos.cgv.domain.payment.gateway;

public class PaymentGatewayException extends RuntimeException {
    private final GatewayFailureType failureType;
    private final String code;

    public PaymentGatewayException(GatewayFailureType failureType, String code) {
        super("Payment gateway request could not be completed");
        this.failureType = failureType;
        this.code = code;
    }

    public GatewayFailureType failureType() {
        return failureType;
    }

    public String code() {
        return code;
    }
}
