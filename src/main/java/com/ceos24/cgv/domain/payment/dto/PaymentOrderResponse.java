package com.ceos24.cgv.domain.payment.dto;

import com.ceos24.cgv.domain.payment.entity.Payment;

// 브라우저가 결제위젯의 requestPayment()에 그대로 넘기는 값이다. 금액도 서버가 정해 내려준다.
public record PaymentOrderResponse(
        String orderId,
        String orderName,
        int amount
) {
    public static PaymentOrderResponse of(Payment payment, String orderName) {
        return new PaymentOrderResponse(payment.getOrderId(), orderName, payment.getAmount());
    }
}
