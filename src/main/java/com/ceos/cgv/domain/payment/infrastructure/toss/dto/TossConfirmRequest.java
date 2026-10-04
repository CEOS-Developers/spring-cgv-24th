package com.ceos.cgv.domain.payment.infrastructure.toss.dto;

import lombok.Builder;

@Builder
public record TossConfirmRequest(String paymentKey, String orderId, long amount) {
}
