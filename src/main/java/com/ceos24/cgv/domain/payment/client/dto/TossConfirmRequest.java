package com.ceos24.cgv.domain.payment.client.dto;

public record TossConfirmRequest(String paymentKey, String orderId, long amount) {
    public TossConfirmRequest {
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200) {
            throw new IllegalArgumentException("결제 키는 필수이며 200자 이하여야 합니다.");
        }
        if (orderId == null || !orderId.matches("[a-zA-Z0-9_=\\-]{6,64}")) {
            throw new IllegalArgumentException("주문번호의 형식이나 길이가 올바르지 않습니다.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("승인 금액은 0보다 커야 합니다.");
        }
    }
}
