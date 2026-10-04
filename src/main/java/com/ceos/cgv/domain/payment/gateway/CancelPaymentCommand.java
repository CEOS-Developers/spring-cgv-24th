package com.ceos.cgv.domain.payment.gateway;

import lombok.Builder;

@Builder
public record CancelPaymentCommand(String paymentKey, String orderId, long cancelAmount,
                                   long refundableAmount, String reason, String idempotencyKey) {
}
