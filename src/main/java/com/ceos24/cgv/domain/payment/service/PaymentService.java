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

import java.time.Clock;
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
	private final Clock clock;

	// completed가 있으면 이미 끝난 취소 요청, 없으면 토스 호출이 필요한 취소 건
	public record CancelStart(PaymentResponse completed, Long cancelId, String paymentKey, String tossOrderId, Integer amount,
							  String reason, String idempotencyKey, boolean retry, int canceledAmount) {
		public static CancelStart completed(PaymentResponse response) {
			return new CancelStart(response, null, null, null, null, null, null, false, 0);
		}

		public static CancelStart toCall(Payment payment, PaymentCancel cancel) {
			return new CancelStart(null, cancel.getId(), payment.getPaymentKey(), payment.getTossOrderId(),
					cancel.getCancelAmount(), cancel.getCancelReason(), cancel.getIdempotencyKey(),
					cancel.getStatus() == PaymentCancelStatus.UNKNOWN, payment.getCanceledAmount());
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
		int updated = paymentRepository.startProcessing(payment.getId(), CONFIRMABLE, Instant.now(clock));
		if (updated == 0) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_CONFIRMABLE);
		}
		return payment;
	}

	// 원래 요청과 복구 작업이 동시에 정산할 수 있으므로 행을 잠그고 정산 가능한 상태일 때만 반영한다
	@Transactional
	public PaymentResponse completeApproval(Long paymentId, TossPaymentResponse toss) {
		Payment payment = getPaymentForUpdate(paymentId);
		if (payment.isApproved()) {
			return toResponse(payment);
		}
		if (!payment.isSettleable()) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_CONFIRMABLE);
		}
		boolean matches = toss.isDone() && payment.getTossOrderId().equals(toss.orderId())
				&& payment.getAmount().equals(toss.totalAmount());
		if (!matches) {
			throw new GeneralException(PaymentErrorStatus.PG_RESPONSE_MISMATCH);
		}

		payment.approve(toss.paymentKey(), toss.method(), toss.approvedAt().toInstant());
		handleApproved(payment);
		return toResponse(payment);
	}

	// 같은 보상 취소가 응답 유실 후 대사 등으로 여러 번 확인될 수 있어, 이미 반영됐으면 다시 반영하지 않는다
	@Transactional
	public PaymentResponse recordCompensation(Long paymentId, TossPaymentResponse canceled) {
		Payment payment = getPaymentForUpdate(paymentId);
		if (payment.getStatus() == PaymentStatus.CANCELED) {
			return toResponse(payment);
		}
		boolean matches = canceled.isCanceled() && payment.getTossOrderId().equals(canceled.orderId())
				&& payment.getAmount().equals(canceled.totalAmount());
		if (!matches) {
			throw new GeneralException(PaymentErrorStatus.PG_RESPONSE_MISMATCH);
		}

		int canceledAmount = payment.getBalanceAmount();
		payment.approve(canceled.paymentKey(), canceled.method(), canceled.approvedAt().toInstant());
		payment.cancelFully();
		handleCompensated(payment);
		PaymentCancel cancel = PaymentCancel.request(payment, payment.getCompensationIdempotencyKey(), null, canceledAmount,
				COMPENSATION_REASON);
		applyTossCancel(cancel, canceled);
		paymentCancelRepository.save(cancel);
		return toResponse(payment);
	}

	public PaymentResponse getPayment(Long userId, Long paymentId) {
		Payment payment = getPayment(paymentId);
		if (!payment.isOwnedBy(userId)) {
			throw new GeneralException(PaymentErrorStatus.NOT_PAYMENT_OWNER);
		}
		return toResponse(payment);
	}

	// Payment 행을 잠가 같은 결제의 동시 취소를 직렬화하고, 취소 금액을 처리 중으로 먼저 예약해 초과 취소를 막는다
	@Transactional
	public CancelStart startCancel(Long userId, Long paymentId, String idempotencyKey, PaymentCancelRequest request) {
		Payment payment = getPaymentForUpdate(paymentId);
		if (!payment.isOwnedBy(userId)) {
			throw new GeneralException(PaymentErrorStatus.NOT_PAYMENT_OWNER);
		}

		PaymentCancel existing = paymentCancelRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			return resumeCancel(payment, existing, request);
		}

		if (payment.getStatus() == PaymentStatus.CANCELED) {
			throw new GeneralException(PaymentErrorStatus.ALREADY_CANCELED);
		}
		if (!payment.isCancelable()) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_CANCELABLE);
		}
		Integer requestedAmount = request.cancelAmount();
		if (payment.getReservation() != null && requestedAmount != null
				&& !requestedAmount.equals(payment.getCancelableAmount())) {
			throw new GeneralException(PaymentErrorStatus.PARTIAL_CANCEL_NOT_SUPPORTED);
		}
		if (payment.getReservation() != null) {
			reservationService.validateCancelable(payment.getReservation());
		}

		int amount = (requestedAmount != null) ? requestedAmount : payment.getCancelableAmount();
		payment.reserveCancel(amount);
		PaymentCancel cancel = paymentCancelRepository.save(
				PaymentCancel.request(payment, idempotencyKey, requestedAmount, amount, request.cancelReason()));
		return CancelStart.toCall(payment, cancel);
	}

	@Transactional
	public PaymentResponse completeCancel(Long paymentId, Long cancelId, TossPaymentResponse toss) {
		Payment payment = getPaymentForUpdate(paymentId);
		PaymentCancel cancel = getCancel(cancelId);
		if (cancel.isPending()) {
			completePendingCancel(payment, cancel, toss);
		}
		return toResponse(payment);
	}

	// 토스가 확정 거절한 첫 요청만 예약을 되돌린다. 재시도(UNKNOWN)의 거절은 settleUnknownCancel에서 토스 상태로 판단한다
	@Transactional
	public void failCancel(Long paymentId, Long cancelId) {
		Payment payment = getPaymentForUpdate(paymentId);
		PaymentCancel cancel = getCancel(cancelId);
		if (cancel.getStatus() == PaymentCancelStatus.REQUESTED) {
			cancel.fail();
			payment.restoreCancel(cancel.getCancelAmount());
		}
	}

	// 토스의 실제 취소 누계(toss)와 로컬 완료 누계를 비교해, 이 취소의 반영 여부가 증명될 때만 확정한다.
	// 다른 취소가 함께 처리 중이거나 취소 시작 이후 완료 누계가 바뀌었으면 판단하지 않고 UNKNOWN을 유지한다
	@Transactional
	public PaymentCancelStatus settleUnknownCancel(Long paymentId, Long cancelId, int canceledAmountAtStart,
												   TossPaymentResponse toss) {
		Payment payment = getPaymentForUpdate(paymentId);
		PaymentCancel cancel = getCancel(cancelId);
		boolean decidable = cancel.getStatus() == PaymentCancelStatus.UNKNOWN
				&& payment.getTossOrderId().equals(toss.orderId()) && payment.getAmount().equals(toss.totalAmount())
				&& toss.balanceAmount() != null && payment.getPendingCancelAmount().equals(cancel.getCancelAmount())
				&& payment.getCanceledAmount() == canceledAmountAtStart;
		if (!decidable) {
			return cancel.getStatus();
		}

		int tossCanceledAmount = toss.totalAmount() - toss.balanceAmount();
		if (tossCanceledAmount == payment.getCanceledAmount()) {
			cancel.fail();
			payment.restoreCancel(cancel.getCancelAmount());
		} else if (tossCanceledAmount == payment.getCanceledAmount() + cancel.getCancelAmount()) {
			completePendingCancel(payment, cancel, toss);
		}
		return cancel.getStatus();
	}

	@Transactional
	public void markCancelUnknown(Long cancelId) {
		PaymentCancel cancel = getCancel(cancelId);
		if (cancel.getStatus() == PaymentCancelStatus.REQUESTED) {
			cancel.markUnknown();
		}
	}

	@Transactional
	public Payment startReconcile(Long userId, Long paymentId) {
		Payment payment = getPayment(paymentId);
		if (!payment.isOwnedBy(userId)) {
			throw new GeneralException(PaymentErrorStatus.NOT_PAYMENT_OWNER);
		}
		return claimReconcile(payment);
	}

	@Transactional
	public Payment startRecovery(Long paymentId) {
		return claimReconcile(getPayment(paymentId));
	}

	public List<Long> findStaleProcessingIds() {
		return paymentRepository.findStaleProcessingIds(Instant.now(clock).minus(Payment.PROCESSING_TIMEOUT));
	}

	@Transactional
	public PaymentResponse fail(Long paymentId) {
		Payment payment = getPaymentForUpdate(paymentId);
		if (payment.isSettleable()) {
			payment.fail();
			handleUnpaidReleased(payment);
		}
		return toResponse(payment);
	}

	@Transactional
	public void markUnknown(Long paymentId) {
		Payment payment = getPaymentForUpdate(paymentId);
		if (payment.getStatus() == PaymentStatus.IN_PROGRESS) {
			payment.markUnknown();
		}
	}

	// UNKNOWN → IN_PROGRESS로 선점해 동시 대사로 재고가 이중 차감되는 것을 막는다.
	// IN_PROGRESS는 처리 제한 시간이 지난 경우(서버 중단 등)에만 가져와 진행 중인 요청과 겹치지 않게 한다
	private Payment claimReconcile(Payment payment) {
		if (!payment.isSettleable()) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_RECONCILABLE);
		}
		Instant now = Instant.now(clock);
		int updated = paymentRepository.startReconcile(payment.getId(), now.minus(Payment.PROCESSING_TIMEOUT), now);
		if (updated == 0) {
			throw new GeneralException(PaymentErrorStatus.PAYMENT_NOT_RECONCILABLE);
		}
		return payment;
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

	// 승인 확정 트랜잭션은 커밋 결과가 모호할 수 있어(커밋 후 연결 끊김 등) 확정 여부와 관계없이 되돌린다. 각 메서드가 현재 상태를 확인한다
	private void handleCompensated(Payment payment) {
		if (payment.getOrder() != null) {
			orderService.restoreCanceledOrder(payment.getOrder());
			payment.getOrder().cancelPayment();
		}
		if (payment.getReservation() != null) {
			reservationService.releaseUnpaid(payment.getReservation());
			reservationService.releaseCanceled(payment.getReservation());
		}
	}

	private CancelStart resumeCancel(Payment payment, PaymentCancel existing, PaymentCancelRequest request) {
		if (!existing.isSameRequest(payment.getId(), request.cancelAmount(), request.cancelReason())) {
			throw new GeneralException(PaymentErrorStatus.IDEMPOTENCY_KEY_CONFLICT);
		}
		return switch (existing.getStatus()) {
			case DONE -> CancelStart.completed(toResponse(payment));
			case REQUESTED -> throw new GeneralException(PaymentErrorStatus.CANCEL_IN_PROGRESS);
			case FAILED -> throw new GeneralException(PaymentErrorStatus.CANCEL_ALREADY_FAILED);
			case UNKNOWN -> CancelStart.toCall(payment, existing);
		};
	}

	private void completePendingCancel(Payment payment, PaymentCancel cancel, TossPaymentResponse toss) {
		applyTossCancel(cancel, toss);
		payment.completeCancel(cancel.getCancelAmount());
		if (payment.getStatus() == PaymentStatus.CANCELED) {
			handleFullyCanceled(payment);
		}
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
