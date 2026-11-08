package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.domain.payment.service.PaymentService.CancelStart;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

	private final PaymentService paymentService;
	private final TossPaymentClient tossPaymentClient;

	public PaymentResponse confirm(Long userId, PaymentConfirmRequest request) {
		Payment payment = paymentService.startApproval(userId, request);
		if (payment.isApproved()) {
			return paymentService.getPayment(userId, payment.getId());
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

		return completeOrCompensate(payment, toss);
	}

	public PaymentResponse reconcile(Long userId, Long paymentId) {
		Payment payment = paymentService.startReconcile(userId, paymentId);

		TossPaymentResponse toss;
		try {
			toss = tossPaymentClient.getByOrderId(payment.getTossOrderId());
		} catch (TossPaymentException e) {
			if (e.isResultUnknown() || !e.isNotFoundPayment()) { // 인증 오류 등 그 외 4xx는 실패로 단정 금지
				paymentService.markUnknown(paymentId);
				throw e;
			}
			paymentService.fail(paymentId);
			return paymentService.getPayment(userId, paymentId);
		}

		if (!toss.isDone()) { // 승인 완료가 아니면 확정도 취소도 하지 않고 재확인 대상으로 남긴다
			paymentService.markUnknown(paymentId);
			throw new GeneralException(PaymentErrorStatus.PG_RESULT_UNKNOWN);
		}
		return completeOrCompensate(payment, toss);
	}

	public PaymentResponse cancel(Long userId, Long paymentId, String idempotencyKey, PaymentCancelRequest request) {
		CancelStart start = paymentService.startCancel(userId, paymentId, idempotencyKey, request);
		if (start.completed() != null) {
			return start.completed();
		}

		TossPaymentResponse toss;
		try {
			toss = tossPaymentClient.cancel(start.paymentKey(), start.reason(), start.amount(), start.idempotencyKey());
		} catch (TossPaymentException e) {
			if (e.isResultUnknown()) {
				paymentService.markCancelUnknown(start.cancelId());
			} else {
				paymentService.failCancel(paymentId, start.cancelId());
			}
			throw e;
		}

		try {
			return paymentService.completeCancel(paymentId, start.cancelId(), toss);
		} catch (RuntimeException e) { // 토스에서는 취소됐는데 DB 반영에 실패 → 같은 멱등키로 재시도할 수 있게 둔다
			paymentService.markCancelUnknown(start.cancelId());
			throw e;
		}
	}

	private PaymentResponse completeOrCompensate(Payment payment, TossPaymentResponse toss) {
		try {
			return paymentService.completeApproval(payment.getId(), toss);
		} catch (RuntimeException e) {
			throw compensate(payment, toss, e);
		}
	}

	// 토스 승인은 끝났지만 주문 확정에 실패한 경우 전액 취소한다. 던질 예외를 반환한다
	private RuntimeException compensate(Payment payment, TossPaymentResponse toss, RuntimeException cause) {
		try {
			TossPaymentResponse canceled = tossPaymentClient.cancel(toss.paymentKey(), PaymentService.COMPENSATION_REASON, null,
					payment.getCompensationIdempotencyKey());
			paymentService.recordCompensation(payment.getId(), canceled);
			return new GeneralException(PaymentErrorStatus.PAYMENT_COMPENSATED);
		} catch (RuntimeException e) {
			log.error("보상 취소 실패 - 수동 확인 필요. paymentId: {}, tossOrderId: {}", payment.getId(), payment.getTossOrderId(), e);
			paymentService.markUnknown(payment.getId());
			return cause;
		}
	}
}
