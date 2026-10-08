package com.ceos24.cgv.global.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

// 토스 Payment 객체 중 우리가 저장하는 필드만 받는다. 토스가 필드를 추가해도 역직렬화가 깨지지 않게 나머지는 무시한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(
        String paymentKey,
        String orderId,
        String status,
        String method,
        int totalAmount,
        OffsetDateTime approvedAt
) {
    private static final String DONE = "DONE";

    public boolean isDone() {
        return DONE.equals(status);
    }
}
