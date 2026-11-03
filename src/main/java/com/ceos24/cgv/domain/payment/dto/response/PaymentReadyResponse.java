package com.ceos24.cgv.domain.payment.dto.response;

import com.ceos24.cgv.domain.concession.entity.Order;

public record PaymentReadyResponse(
		String orderId,
		Integer amount,
		String orderName
) {
	public static PaymentReadyResponse from(Order order) {
		return new PaymentReadyResponse(
				order.getTossOrderId(),
				order.getTotalPrice(),
				"CGV 매점 주문 #" + order.getId()
		);
	}
}
