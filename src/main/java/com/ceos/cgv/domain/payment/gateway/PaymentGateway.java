package com.ceos.cgv.domain.payment.gateway;

public interface PaymentGateway {
    GatewayPayment confirm(ConfirmPaymentCommand command);
    GatewayPayment getPayment(String paymentKey);
    GatewayPayment cancel(CancelPaymentCommand command);
}
