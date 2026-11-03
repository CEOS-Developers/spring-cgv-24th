package com.ceos24.cgv.domain.payment.dto.response;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(
		Long paymentId, String orderId, PaymentStatus status, Integer amount, Integer balanceAmount, String method, Instant approvedAt
) {
	public static PaymentResponse from(Payment payment) {
		return new PaymentResponse(payment.getId(), payment.getTossOrderId(), payment.getStatus(), payment.getAmount(),
				payment.getBalanceAmount(), payment.getMethod(), payment.getApprovedAt());
	}
}
