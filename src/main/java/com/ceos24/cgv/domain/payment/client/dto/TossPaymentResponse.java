package com.ceos24.cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
		String paymentKey, String orderId, String status, Integer totalAmount, Integer balanceAmount,
		String method, OffsetDateTime approvedAt, List<Cancel> cancels
) {
	public boolean isDone() {
		return "DONE".equals(status);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Cancel(String transactionKey, Integer cancelAmount, String cancelReason, OffsetDateTime canceledAt) {
	}
}
