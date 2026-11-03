package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.exception.ReservationErrorStatus;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentCancelRepository paymentCancelRepository;
	@Mock
	private ReservationService reservationService;

	@InjectMocks
	private PaymentService paymentService;

	private Payment readyPayment() {
		return withId(Payment.forOrder(order(1L, user(10L), theater(2L), 15_000)), 100L);
	}

	@Test
	void 클라이언트가_보낸_금액이_서버_금액과_다르면_승인을_시작하지_않는다() {
		Payment payment = readyPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 100)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PAYMENT_AMOUNT_MISMATCH);
		verify(paymentRepository, never()).updateStatusIfMatches(anyLong(), anyList(), any());
	}

	@Test
	void 다른_사용자의_결제는_승인할_수_없다() {
		Payment payment = readyPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.startApproval(99L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.NOT_PAYMENT_OWNER);
	}

	@Test
	void 이미_같은_paymentKey로_승인된_결제는_상태를_바꾸지_않고_그대로_반환한다() {
		Payment payment = readyPayment();
		payment.approve("pk-1", "카드", Instant.now());
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));

		Payment result = paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000));

		assertThat(result.isApproved()).isTrue();
		verify(paymentRepository, never()).updateStatusIfMatches(anyLong(), anyList(), any());
	}

	@Test
	void 다른_요청이_먼저_승인을_시작했다면_중복_승인을_거절한다() {
		Payment payment = readyPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		when(paymentRepository.updateStatusIfMatches(eq(100L), anyList(), any())).thenReturn(0);

		assertThatThrownBy(() -> paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PAYMENT_NOT_CONFIRMABLE);
	}

	@Test
	void 다른_사용자의_결제는_조회할_수_없다() {
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(readyPayment()));

		assertThatThrownBy(() -> paymentService.getPayment(99L, 100L))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.NOT_PAYMENT_OWNER);
	}

	@Test
	void 결과_불명이_아닌_결제는_대사할_수_없다() {
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(readyPayment()));

		assertThatThrownBy(() -> paymentService.startReconcile(10L, 100L))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PAYMENT_NOT_RECONCILABLE);
		verify(paymentRepository, never()).updateStatusIfMatches(anyLong(), anyList(), any());
	}

	@Test
	void 결과_불명_결제는_승인_진행_중으로_선점하고_대사를_시작한다() {
		Payment payment = readyPayment();
		payment.markUnknown();
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
		when(paymentRepository.updateStatusIfMatches(eq(100L), anyList(), any())).thenReturn(1);

		Payment result = paymentService.startReconcile(10L, 100L);

		assertThat(result).isSameAs(payment);
	}

	private Payment donePayment() {
		Payment payment = readyPayment();
		payment.approve("pk-1", "카드", Instant.now());
		return payment;
	}

	@Test
	void 같은_멱등키로_이미_완료된_취소는_토스_호출_대상이_아니다() {
		Payment payment = donePayment();
		PaymentCancel done = PaymentCancel.request(payment, "key-1", 15_000, "단순 변심");
		done.complete("tx-1", Instant.now());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(done));

		PaymentService.CancelStart result = paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", null));

		assertThat(result.completed()).isNotNull();
		assertThat(payment.getBalanceAmount()).isEqualTo(15_000);
	}

	@Test
	void 다른_결제에_사용된_멱등키는_거절한다() {
		Payment payment = donePayment();
		Payment other = withId(Payment.forOrder(order(2L, user(10L), theater(2L), 5_000)), 101L);
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.findByIdempotencyKey("key-1"))
				.thenReturn(Optional.of(PaymentCancel.request(other, "key-1", 5_000, "단순 변심")));

		assertThatThrownBy(() -> paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", null)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.IDEMPOTENCY_KEY_CONFLICT);
	}

	@Test
	void 이미_전액_취소된_결제는_취소할_수_없다() {
		Payment payment = donePayment();
		payment.cancelFully();
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", null)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.ALREADY_CANCELED);
		verify(paymentCancelRepository, never()).save(any());
	}

	@Test
	void 취소_금액이_없으면_잔액_전액을_예약하고_토스_호출_대상으로_반환한다() {
		Payment payment = donePayment();
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.save(any(PaymentCancel.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 200L));

		PaymentService.CancelStart result = paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", null));

		assertThat(result.completed()).isNull();
		assertThat(result.cancelId()).isEqualTo(200L);
		assertThat(result.amount()).isEqualTo(15_000);
		assertThat(payment.getBalanceAmount()).isZero();
	}

	private Reservation heldReservation() {
		var screen = screen(1L, theater(2L));
		var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now().plusHours(1));
		return reservation(50L, user(10L), schedule, "A1");
	}

	private Payment reservationPayment() {
		return withId(Payment.forReservation(heldReservation(), "테스트 영화 1매"), 100L);
	}

	private TossPaymentResponse doneToss(Payment payment) {
		return new TossPaymentResponse("pk-1", payment.getTossOrderId(), "DONE", payment.getAmount(), payment.getAmount(),
				"카드", OffsetDateTime.now(), null);
	}

	@Test
	void 예매_결제는_선점이_만료되면_결제_상태를_바꾸지_않고_거절한다() {
		Payment payment = reservationPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		doThrow(new GeneralException(ReservationErrorStatus.RESERVATION_HOLD_EXPIRED)).when(reservationService).startPaying(50L);

		assertThatThrownBy(() -> paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(ReservationErrorStatus.RESERVATION_HOLD_EXPIRED);
		verify(paymentRepository, never()).updateStatusIfMatches(anyLong(), anyList(), any());
	}

	@Test
	void 예매_결제는_승인_시작_전에_예매를_결제_진행으로_전환한다() {
		Payment payment = reservationPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		when(paymentRepository.updateStatusIfMatches(eq(100L), anyList(), any())).thenReturn(1);

		paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000));

		var inOrder = inOrder(reservationService, paymentRepository);
		inOrder.verify(reservationService).startPaying(50L);
		inOrder.verify(paymentRepository).updateStatusIfMatches(eq(100L), anyList(), any());
	}

	@Test
	void 결과_불명_결제를_재시도할_때는_예매를_다시_전환하지_않는다() {
		Payment payment = reservationPayment();
		payment.markUnknown();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		when(paymentRepository.updateStatusIfMatches(eq(100L), anyList(), any())).thenReturn(1);

		paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000));

		verify(reservationService, never()).startPaying(anyLong());
	}

	@Test
	void 예매_결제_승인이_완료되면_예매를_확정한다() {
		Payment payment = reservationPayment();
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

		paymentService.completeApproval(100L, doneToss(payment));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		verify(reservationService).confirmPaid(payment.getReservation());
	}

	@Test
	void 예매_결제가_실패하면_예매를_해제한다() {
		Payment payment = reservationPayment();
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

		paymentService.fail(100L);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		verify(reservationService).releaseUnpaid(payment.getReservation());
	}

	@Test
	void 결과_불명으로_표시해도_예매는_건드리지_않는다() {
		Payment payment = reservationPayment();
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

		paymentService.markUnknown(100L);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
		verifyNoInteractions(reservationService);
	}

	@Test
	void 예매_결제_보상_취소를_기록하면_예매를_해제한다() {
		Payment payment = reservationPayment();
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

		paymentService.recordCompensation(100L, doneToss(payment));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		verify(reservationService).releaseUnpaid(payment.getReservation());
	}

	@Test
	void 예매_결제_취소를_시작할_때_취소_가능_시간을_검증한다() {
		Payment payment = reservationPayment();
		payment.approve("pk-1", "카드", Instant.now());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.save(any(PaymentCancel.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 200L));

		paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", null));

		verify(reservationService).validateCancelable(payment.getReservation());
	}

	@Test
	void 취소_가능_시간이_지난_예매_결제는_취소를_예약하지_않는다() {
		Payment payment = reservationPayment();
		payment.approve("pk-1", "카드", Instant.now());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		doThrow(new GeneralException(ReservationErrorStatus.CANCEL_DEADLINE_PASSED)).when(reservationService).validateCancelable(any());

		assertThatThrownBy(() -> paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", null)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(ReservationErrorStatus.CANCEL_DEADLINE_PASSED);
		assertThat(payment.getBalanceAmount()).isEqualTo(15_000);
		verify(paymentCancelRepository, never()).save(any());
	}

	@Test
	void 예매_결제_취소로_잔액이_0이_되면_예매를_해제한다() {
		Payment payment = reservationPayment();
		payment.approve("pk-1", "카드", Instant.now());
		payment.reserveCancel(15_000);
		PaymentCancel cancel = withId(PaymentCancel.request(payment, "key-1", 15_000, "단순 변심"), 200L);
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.findById(200L)).thenReturn(Optional.of(cancel));

		paymentService.completeCancel(100L, 200L, doneToss(payment));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		verify(reservationService).releaseCanceled(payment.getReservation());
	}
}
