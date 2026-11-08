package com.cgvclone.cgv.domain.payment;

public record PaymentConfirmRequest(
        String orderId,
        String paymentKey,
        Integer amount
) {
}
