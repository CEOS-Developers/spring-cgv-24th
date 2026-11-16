package com.ceos24.cgv.domain.payment.dto;

public record TossConfirmRequest(String paymentKey, String orderId, Long amount) {}
