package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
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
		return reconcileWithToss(paymentService.startReconcile(userId, paymentId));
	}

	// 처리 제한 시간이 지난 IN_PROGRESS(승인 도중 서버 중단 등)와 UNKNOWN을 토스 상태로 정리한다
	public void recoverStalePayments() {
		for (Long paymentId : paymentService.findStaleProcessingIds()) {
			try {
				reconcileWithToss(paymentService.startRecovery(paymentId));
			} catch (RuntimeException e) {
				log.warn("결제 자동 복구 보류 - paymentId: {}, reason: {}", paymentId, e.getMessage());
			}
		}
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
				throw e;
			}
			if (start.retry()) {
				return settleRejectedRetry(userId, paymentId, start, e);
			}
			paymentService.failCancel(paymentId, start.cancelId());
			throw e;
		}

		try {
			return paymentService.completeCancel(paymentId, start.cancelId(), toss);
		} catch (RuntimeException e) { // 토스에서는 취소됐는데 DB 반영에 실패 → 같은 멱등키로 재시도할 수 있게 둔다
			paymentService.markCancelUnknown(start.cancelId());
			throw e;
		}
	}

	// 토스 조회 결과로 확정하고, 승인 완료·취소 완료·승인 불가가 아니면 결과 불명으로 남겨 다시 확인하게 한다
	private PaymentResponse reconcileWithToss(Payment payment) {
		TossPaymentResponse toss;
		try {
			toss = tossPaymentClient.getByOrderId(payment.getTossOrderId());
		} catch (TossPaymentException e) {
			if (e.isResultUnknown() || !e.isNotFoundPayment()) { // 인증 오류 등 그 외 4xx는 실패로 단정 금지
				paymentService.markUnknown(payment.getId());
				throw e;
			}
			return paymentService.fail(payment.getId());
		}

		if (toss.isDone()) {
			return completeOrCompensate(payment, toss);
		}
		if (toss.isCanceled()) { // 승인이 로컬에 확정되지 않은 결제가 취소됐다면 보상 취소가 반영되지 못한 것이다
			try {
				return paymentService.recordCompensation(payment.getId(), toss);
			} catch (RuntimeException e) {
				paymentService.markUnknown(payment.getId());
				throw e;
			}
		}
		if (toss.isApprovalFailed()) {
			return paymentService.fail(payment.getId());
		}
		paymentService.markUnknown(payment.getId());
		throw new GeneralException(PaymentErrorStatus.PG_RESULT_UNKNOWN);
	}

	// 재시도는 앞선 요청이 이미 반영됐을 수 있어 거절만으로 실패를 확정하지 않고 토스의 현재 취소 누계로 판단한다
	private PaymentResponse settleRejectedRetry(Long userId, Long paymentId, CancelStart start, TossPaymentException rejection) {
		TossPaymentResponse current;
		try {
			current = tossPaymentClient.getByOrderId(start.tossOrderId());
		} catch (TossPaymentException e) {
			throw new GeneralException(PaymentErrorStatus.PG_RESULT_UNKNOWN);
		}

		PaymentCancelStatus status = paymentService.settleUnknownCancel(paymentId, start.cancelId(), start.canceledAmount(),
				current);
		if (status == PaymentCancelStatus.DONE) {
			return paymentService.getPayment(userId, paymentId);
		}
		if (status == PaymentCancelStatus.FAILED) {
			throw rejection;
		}
		throw new GeneralException(PaymentErrorStatus.PG_RESULT_UNKNOWN);
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
		} catch (RuntimeException e) { // 보상 취소 성공 후 기록 실패도 여기로 온다 → UNKNOWN으로 두면 대사가 토스 CANCELED를 보고 복구한다
			log.error("보상 취소 실패 - 수동 확인 필요. paymentId: {}, tossOrderId: {}", payment.getId(), payment.getTossOrderId(), e);
			paymentService.markUnknown(payment.getId());
			return cause;
		}
	}
}
