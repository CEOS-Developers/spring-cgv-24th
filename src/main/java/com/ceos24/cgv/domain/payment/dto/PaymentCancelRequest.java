package com.ceos24.cgv.domain.payment.dto;

public record PaymentCancelRequest(String cancelReason, Long cancelAmount, Long orderItemId, Long cancelQuantity) {}
