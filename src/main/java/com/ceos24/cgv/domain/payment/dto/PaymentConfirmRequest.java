package com.ceos24.cgv.domain.payment.dto;

public record PaymentConfirmRequest(String paymentKey, String orderId, Long amount) {}
