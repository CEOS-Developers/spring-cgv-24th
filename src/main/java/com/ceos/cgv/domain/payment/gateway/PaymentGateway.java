package com.ceos.cgv.domain.payment.gateway;

public interface PaymentGateway {
    GatewayPayment confirm(ConfirmPaymentCommand command);
    GatewayPayment getPayment(String paymentKey);
    GatewayPayment cancel(CancelPaymentCommand command);

    /** 외부 호출 없이 연동 설정만 확인한다. 사용할 수 없으면 PaymentGatewayException을 던진다. */
    default void verifyReady() {
    }
}
