package com.ceos.cgv.domain.payment.gateway;

import lombok.Builder;

@Builder
public record ConfirmPaymentCommand(String paymentKey, String orderId, long amount, String idempotencyKey) {
}
