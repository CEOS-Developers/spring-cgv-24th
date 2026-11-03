package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.concession.service.OrderService;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private static final List<PaymentStatus> CONFIRMABLE = List.of(PaymentStatus.READY, PaymentStatus.UNKNOWN);

	private final PaymentRepository paymentRepository;
	private final OrderService orderService;

	@Transactional
	public Payment startApproval(Long userId, PaymentConfirmRequest request) {
		Payment payment = paymentRepository.findByTossOrderId(request.orderId())
				.orElseThrow(() -> new GeneralException(PaymentErrorStatus.PAYMENT_NOT_FOUND));

		if (!payment.isOwnedBy(userId)) {
			throw new GeneralException(PaymentErrorStatus.NOT_PAYMENT_OWNER);
		}
		if (!payment.getAmount().equals(request.amount())) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_AMOUNT_MISMATCH);
		}
		if (payment.isApproved() && request.paymentKey().equals(payment.getPaymentKey())) {
			return payment;
		}

		int updated = paymentRepository.updateStatusIfMatches(payment.getId(), CONFIRMABLE, PaymentStatus.IN_PROGRESS);
		if (updated == 0) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_CONFIRMABLE);
		}
		return payment;
	}

	@Transactional
	public PaymentResponse completeApproval(Long paymentId, TossPaymentResponse toss) {
		Payment payment = getPayment(paymentId);
		boolean matches = "DONE".equals(toss.status()) && payment.getTossOrderId().equals(toss.orderId())
				&& payment.getAmount().equals(toss.totalAmount());
		if (!matches) {
			throw new GeneralException(PaymentErrorStatus.PG_RESPONSE_MISMATCH);
		}

		payment.approve(toss.paymentKey(), toss.method(), toss.approvedAt().toInstant());
		if (payment.getOrder() != null) {
			orderService.completePaidOrder(payment.getOrder());
		}
		return PaymentResponse.from(payment);
	}

	@Transactional
	public void recordCompensation(Long paymentId, TossPaymentResponse canceled) {
		Payment payment = getPayment(paymentId);
		payment.approve(canceled.paymentKey(), canceled.method(), canceled.approvedAt().toInstant());
		payment.cancelFully();
		if (payment.getOrder() != null) {
			payment.getOrder().cancelPayment();
		}
	}

	@Transactional
	public void fail(Long paymentId) {
		getPayment(paymentId).fail();
	}

	@Transactional
	public void markUnknown(Long paymentId) {
		getPayment(paymentId).markUnknown();
	}

	private Payment getPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new GeneralException(PaymentErrorStatus.PAYMENT_NOT_FOUND));
	}
}
