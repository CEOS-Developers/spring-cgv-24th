package com.ceos24.cgv.domain.payment.client.dto;

public record TossConfirmRequest(String paymentKey, String orderId, Integer amount) {
}
