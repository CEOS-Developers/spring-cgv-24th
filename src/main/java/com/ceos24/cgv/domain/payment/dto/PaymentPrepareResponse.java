package com.ceos24.cgv.domain.payment.dto;

public record PaymentPrepareResponse(String orderId, Long amount, String orderName, Long paymentId) {}
