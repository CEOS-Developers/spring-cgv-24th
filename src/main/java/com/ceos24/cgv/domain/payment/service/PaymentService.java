package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.concession.service.OrderService;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	public static final String COMPENSATION_REASON = "주문 확정 실패로 인한 자동 취소";

	private static final List<PaymentStatus> CONFIRMABLE = List.of(PaymentStatus.READY, PaymentStatus.UNKNOWN);

	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final OrderService orderService;
	private final ReservationService reservationService;

	// completed가 있으면 이미 끝난 취소 요청, 없으면 토스 호출이 필요한 취소 건
	public record CancelStart(PaymentResponse completed, Long cancelId, String paymentKey, Integer amount, String reason,
							  String idempotencyKey) {
		public static CancelStart completed(PaymentResponse response) {
			return new CancelStart(response, null, null, null, null, null);
		}

		public static CancelStart toCall(Payment payment, PaymentCancel cancel) {
			return new CancelStart(null, cancel.getId(), payment.getPaymentKey(), cancel.getCancelAmount(),
					cancel.getCancelReason(), cancel.getIdempotencyKey());
		}
	}

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

		// UNKNOWN 재시도는 예매가 이미 PAYING이다. 벌크 UPDATE 전에 필요한 값을 먼저 읽어 둔다
		if (payment.getReservation() != null && payment.getStatus() != PaymentStatus.UNKNOWN) {
			reservationService.startPaying(payment.getReservation().getId());
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
		boolean matches = toss.isDone() && payment.getTossOrderId().equals(toss.orderId())
				&& payment.getAmount().equals(toss.totalAmount());
		if (!matches) {
			throw new GeneralException(PaymentErrorStatus.PG_RESPONSE_MISMATCH);
		}

		payment.approve(toss.paymentKey(), toss.method(), toss.approvedAt().toInstant());
		handleApproved(payment);
		return toResponse(payment);
	}

	@Transactional
	public void recordCompensation(Long paymentId, TossPaymentResponse canceled) {
		Payment payment = getPayment(paymentId);
		payment.approve(canceled.paymentKey(), canceled.method(), canceled.approvedAt().toInstant());
		payment.cancelFully();
		if (payment.getOrder() != null) {
			payment.getOrder().cancelPayment();
		}
		handleUnpaidReleased(payment);
		PaymentCancel cancel = PaymentCancel.request(payment, payment.getCompensationIdempotencyKey(), payment.getAmount(),
				COMPENSATION_REASON);
		applyTossCancel(cancel, canceled);
		paymentCancelRepository.save(cancel);
	}

	public PaymentResponse getPayment(Long userId, Long paymentId) {
		Payment payment = getPayment(paymentId);
		if (!payment.isOwnedBy(userId)) {
			throw new GeneralException(PaymentErrorStatus.NOT_PAYMENT_OWNER);
		}
		return toResponse(payment);
	}

	// Payment 행을 잠가 같은 결제의 동시 취소를 직렬화하고, 잔액을 먼저 차감(예약)해 초과 취소를 막는다
	@Transactional
	public CancelStart startCancel(Long userId, Long paymentId, String idempotencyKey, PaymentCancelRequest request) {
		Payment payment = getPaymentForUpdate(paymentId);
		if (!payment.isOwnedBy(userId)) {
			throw new GeneralException(PaymentErrorStatus.NOT_PAYMENT_OWNER);
		}

		PaymentCancel existing = paymentCancelRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			return resumeCancel(payment, existing);
		}

		if (payment.getStatus() == PaymentStatus.CANCELED) {
			throw new GeneralException(PaymentErrorStatus.ALREADY_CANCELED);
		}
		if (!payment.isCancelable()) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_CANCELABLE);
		}
		Integer requestedAmount = request.cancelAmount();
		if (payment.getReservation() != null && requestedAmount != null
				&& !requestedAmount.equals(payment.getBalanceAmount())) {
			throw new GeneralException(PaymentErrorStatus.PARTIAL_CANCEL_NOT_SUPPORTED);
		}
		if (payment.getReservation() != null) {
			reservationService.validateCancelable(payment.getReservation());
		}

		int amount = (requestedAmount != null) ? requestedAmount : payment.getBalanceAmount();
		payment.reserveCancel(amount);
		PaymentCancel cancel = paymentCancelRepository.save(
				PaymentCancel.request(payment, idempotencyKey, amount, request.cancelReason()));
		return CancelStart.toCall(payment, cancel);
	}

	@Transactional
	public PaymentResponse completeCancel(Long paymentId, Long cancelId, TossPaymentResponse toss) {
		Payment payment = getPaymentForUpdate(paymentId);
		PaymentCancel cancel = getCancel(cancelId);
		if (cancel.isPending()) {
			applyTossCancel(cancel, toss);
			payment.applyCanceledStatus();
			if (payment.getBalanceAmount() == 0) {
				handleFullyCanceled(payment);
			}
		}
		return toResponse(payment);
	}

	// 토스가 확정 거절한 취소만 예약을 되돌린다. 재시도(UNKNOWN)는 앞선 요청이 성공했을 수 있어 유지한다
	@Transactional
	public void failCancel(Long paymentId, Long cancelId) {
		Payment payment = getPaymentForUpdate(paymentId);
		PaymentCancel cancel = getCancel(cancelId);
		if (cancel.getStatus() == PaymentCancelStatus.REQUESTED) {
			cancel.fail();
			payment.restoreCancel(cancel.getCancelAmount());
		}
	}

	@Transactional
	public void markCancelUnknown(Long cancelId) {
		PaymentCancel cancel = getCancel(cancelId);
		if (cancel.getStatus() == PaymentCancelStatus.REQUESTED) {
			cancel.markUnknown();
		}
	}

	// UNKNOWN → IN_PROGRESS로 선점해 동시 대사로 재고가 이중 차감되는 것을 막는다
	@Transactional
	public Payment startReconcile(Long userId, Long paymentId) {
		Payment payment = getPayment(paymentId);
		if (!payment.isOwnedBy(userId)) {
			throw new GeneralException(PaymentErrorStatus.NOT_PAYMENT_OWNER);
		}
		if (payment.getStatus() != PaymentStatus.UNKNOWN) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_RECONCILABLE);
		}

		int updated = paymentRepository.updateStatusIfMatches(paymentId, List.of(PaymentStatus.UNKNOWN), PaymentStatus.IN_PROGRESS);
		if (updated == 0) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_RECONCILABLE);
		}
		return payment;
	}

	@Transactional
	public void fail(Long paymentId) {
		Payment payment = getPayment(paymentId);
		payment.fail();
		handleUnpaidReleased(payment);
	}

	@Transactional
	public void markUnknown(Long paymentId) {
		getPayment(paymentId).markUnknown();
	}

	private void handleApproved(Payment payment) {
		if (payment.getOrder() != null) {
			orderService.completePaidOrder(payment.getOrder());
		}
		if (payment.getReservation() != null) {
			reservationService.confirmPaid(payment.getReservation());
		}
	}

	private void handleUnpaidReleased(Payment payment) {
		if (payment.getReservation() != null) {
			reservationService.releaseUnpaid(payment.getReservation());
		}
	}

	private void handleFullyCanceled(Payment payment) {
		if (payment.getOrder() != null) {
			orderService.restoreCanceledOrder(payment.getOrder());
		}
		if (payment.getReservation() != null) {
			reservationService.releaseCanceled(payment.getReservation());
		}
	}

	private CancelStart resumeCancel(Payment payment, PaymentCancel existing) {
		if (!existing.getPayment().getId().equals(payment.getId())) {
			throw new GeneralException(PaymentErrorStatus.IDEMPOTENCY_KEY_CONFLICT);
		}
		return switch (existing.getStatus()) {
			case DONE -> CancelStart.completed(toResponse(payment));
			case REQUESTED -> throw new GeneralException(PaymentErrorStatus.CANCEL_IN_PROGRESS);
			case FAILED -> throw new GeneralException(PaymentErrorStatus.CANCEL_ALREADY_FAILED);
			case UNKNOWN -> CancelStart.toCall(payment, existing);
		};
	}

	private void applyTossCancel(PaymentCancel cancel, TossPaymentResponse toss) {
		List<TossPaymentResponse.Cancel> cancels = toss.cancels();
		if (cancels == null || cancels.isEmpty()) {
			cancel.complete(null, Instant.now());
			return;
		}
		TossPaymentResponse.Cancel last = cancels.get(cancels.size() - 1);
		cancel.complete(last.transactionKey(), last.canceledAt().toInstant());
	}

	private PaymentResponse toResponse(Payment payment) {
		return PaymentResponse.from(payment, paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId()));
	}

	private Payment getPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new GeneralException(PaymentErrorStatus.PAYMENT_NOT_FOUND));
	}

	private Payment getPaymentForUpdate(Long paymentId) {
		return paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new GeneralException(PaymentErrorStatus.PAYMENT_NOT_FOUND));
	}

	private PaymentCancel getCancel(Long cancelId) {
		return paymentCancelRepository.findById(cancelId)
				.orElseThrow(() -> new GeneralException(PaymentErrorStatus.PAYMENT_NOT_FOUND));
	}
}
