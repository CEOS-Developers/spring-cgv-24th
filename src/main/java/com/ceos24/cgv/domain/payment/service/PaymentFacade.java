package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

	private final PaymentService paymentService;
	private final TossPaymentClient tossPaymentClient;

	public PaymentResponse confirm(Long userId, PaymentConfirmRequest request) {
		Payment payment = paymentService.startApproval(userId, request);
		if (payment.isApproved()) {
			return PaymentResponse.from(payment);
		}

		TossPaymentResponse toss;
		try {
			toss = tossPaymentClient.confirm(request.paymentKey(), payment.getTossOrderId(), payment.getAmount(),
					payment.getConfirmIdempotencyKey());
		} catch (TossPaymentException e) {
			if (e.isResultUnknown()) {
				paymentService.markUnknown(payment.getId());
			} else {
				paymentService.fail(payment.getId());
			}
			throw e;
		}

		try {
			return paymentService.completeApproval(payment.getId(), toss);
		} catch (RuntimeException e) {
			paymentService.markUnknown(payment.getId());
			throw e;
		}
	}
}
