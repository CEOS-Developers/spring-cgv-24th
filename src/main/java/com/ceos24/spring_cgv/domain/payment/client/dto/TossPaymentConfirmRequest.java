package com.ceos24.spring_cgv.domain.payment.client.dto;

import lombok.Builder;

@Builder
public record TossPaymentConfirmRequest(

        String paymentKey,
        String orderId,
        Integer amount
){
    static public TossPaymentConfirmRequest of(String paymentKey, String orderId, Integer amount){
        return TossPaymentConfirmRequest.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .amount(amount)
                .build();
    }
}
