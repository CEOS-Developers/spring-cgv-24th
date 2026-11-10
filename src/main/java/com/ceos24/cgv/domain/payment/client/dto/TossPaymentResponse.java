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

	public boolean isCanceled() {
		return "CANCELED".equals(status);
	}

	// ABORTED(승인 실패), EXPIRED(승인 없이 유효 시간 경과)는 이후에도 승인될 수 없는 종결 상태다
	public boolean isApprovalFailed() {
		return "ABORTED".equals(status) || "EXPIRED".equals(status);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Cancel(String transactionKey, Integer cancelAmount, String cancelReason, OffsetDateTime canceledAt) {
	}
}
