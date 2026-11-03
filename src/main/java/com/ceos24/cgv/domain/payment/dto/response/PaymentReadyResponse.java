package com.ceos24.cgv.domain.payment.dto.response;

import com.ceos24.cgv.domain.payment.entity.Payment;

public record PaymentReadyResponse(
		String orderId,
		Integer amount,
		String orderName
) {
	public static PaymentReadyResponse from(Payment payment) {
		return new PaymentReadyResponse(payment.getTossOrderId(), payment.getAmount(), payment.getOrderName());
	}
}
