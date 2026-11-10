package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.concession.service.OrderService;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.exception.ReservationErrorStatus;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-03T01:00:00Z");

	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentCancelRepository paymentCancelRepository;
	@Mock
	private ReservationService reservationService;
	@Mock
	private OrderService orderService;

	private Clock clock;
	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
		paymentService = new PaymentService(paymentRepository, paymentCancelRepository, orderService, reservationService, clock);
	}

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
		verify(paymentRepository, never()).startProcessing(anyLong(), anyList(), any());
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
		verify(paymentRepository, never()).startProcessing(anyLong(), anyList(), any());
	}

	@Test
	void 다른_요청이_먼저_승인을_시작했다면_중복_승인을_거절한다() {
		Payment payment = readyPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		when(paymentRepository.startProcessing(eq(100L), anyList(), any())).thenReturn(0);

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
		verify(paymentRepository, never()).startReconcile(anyLong(), any(), any());
	}

	@Test
	void 결과_불명_결제는_승인_진행_중으로_선점하고_대사를_시작한다() {
		Payment payment = readyPayment();
		payment.markUnknown();
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
		when(paymentRepository.startReconcile(eq(100L), any(), any())).thenReturn(1);

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
		PaymentCancel done = PaymentCancel.request(payment, "key-1", null, 15_000, "단순 변심");
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
				.thenReturn(Optional.of(PaymentCancel.request(other, "key-1", null, 5_000, "단순 변심")));

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
		assertThat(payment.getCancelableAmount()).isZero();
		assertThat(payment.getBalanceAmount()).isEqualTo(15_000);
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

	private TossPaymentResponse canceledToss(Payment payment) {
		return new TossPaymentResponse("pk-1", payment.getTossOrderId(), "CANCELED", payment.getAmount(), 0,
				"카드", OffsetDateTime.now(), null);
	}

	private Payment inProgress(Payment payment) {
		ReflectionTestUtils.setField(payment, "status", PaymentStatus.IN_PROGRESS);
		return payment;
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
		verify(paymentRepository, never()).startProcessing(anyLong(), anyList(), any());
	}

	@Test
	void 예매_결제는_승인_시작_전에_예매를_결제_진행으로_전환한다() {
		Payment payment = reservationPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		when(paymentRepository.startProcessing(eq(100L), anyList(), any())).thenReturn(1);

		paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000));

		var inOrder = inOrder(reservationService, paymentRepository);
		inOrder.verify(reservationService).startPaying(50L);
		inOrder.verify(paymentRepository).startProcessing(eq(100L), anyList(), any());
	}

	@Test
	void 결과_불명_결제를_재시도할_때는_예매를_다시_전환하지_않는다() {
		Payment payment = reservationPayment();
		payment.markUnknown();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		when(paymentRepository.startProcessing(eq(100L), anyList(), any())).thenReturn(1);

		paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000));

		verify(reservationService, never()).startPaying(anyLong());
	}

	@Test
	void 예매_결제_승인이_완료되면_예매를_확정한다() {
		Payment payment = inProgress(reservationPayment());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.completeApproval(100L, doneToss(payment));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		verify(reservationService).confirmPaid(payment.getReservation());
	}

	@Test
	void 예매_결제가_실패하면_예매를_해제한다() {
		Payment payment = inProgress(reservationPayment());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.fail(100L);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		verify(reservationService).releaseUnpaid(payment.getReservation());
	}

	@Test
	void 결과_불명으로_표시해도_예매는_건드리지_않는다() {
		Payment payment = inProgress(reservationPayment());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.markUnknown(100L);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
		verifyNoInteractions(reservationService);
	}

	@Test
	void 예매_결제_보상_취소를_기록하면_예매를_해제한다() {
		Payment payment = inProgress(reservationPayment());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.recordCompensation(100L, canceledToss(payment));

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
		PaymentCancel cancel = withId(PaymentCancel.request(payment, "key-1", null, 15_000, "단순 변심"), 200L);
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.findById(200L)).thenReturn(Optional.of(cancel));

		paymentService.completeCancel(100L, 200L, doneToss(payment));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		verify(reservationService).releaseCanceled(payment.getReservation());
	}

	private TossPaymentResponse tossWithBalance(Payment payment, int balanceAmount) {
		OffsetDateTime now = OffsetDateTime.now();
		int canceled = payment.getAmount() - balanceAmount;
		List<TossPaymentResponse.Cancel> cancels = canceled == 0 ? List.of()
				: List.of(new TossPaymentResponse.Cancel("tx-1", canceled, "단순 변심", now));
		String status = canceled == 0 ? "DONE" : (balanceAmount == 0 ? "CANCELED" : "PARTIAL_CANCELED");
		return new TossPaymentResponse("pk-1", payment.getTossOrderId(), status, payment.getAmount(), balanceAmount, "카드", now,
				cancels);
	}

	private PaymentCancel storedCancel(Payment payment, Integer requestedAmount, int cancelAmount, String reason) {
		PaymentCancel cancel = withId(PaymentCancel.request(payment, "key-1", requestedAmount, cancelAmount, reason), 200L);
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(cancel));
		return cancel;
	}

	@Test
	void 같은_멱등키와_같은_요청이면_결과_불명_취소를_최초_금액으로_다시_호출한다() {
		Payment payment = donePayment();
		payment.reserveCancel(3_000);
		storedCancel(payment, 3_000, 3_000, "단순 변심").markUnknown();

		PaymentService.CancelStart result = paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", 3_000));

		assertThat(result.cancelId()).isEqualTo(200L);
		assertThat(result.amount()).isEqualTo(3_000);
		assertThat(result.retry()).isTrue();
		assertThat(payment.getPendingCancelAmount()).isEqualTo(3_000);
		verify(paymentCancelRepository, never()).save(any());
	}

	@Test
	void 같은_멱등키로_금액이_다른_요청은_충돌로_거절한다() {
		Payment payment = donePayment();
		payment.reserveCancel(3_000);
		storedCancel(payment, 3_000, 3_000, "단순 변심").markUnknown();

		assertThatThrownBy(() -> paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", 5_000)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.IDEMPOTENCY_KEY_CONFLICT);
		assertThat(payment.getPendingCancelAmount()).isEqualTo(3_000);
		verify(paymentCancelRepository, never()).save(any());
	}

	@Test
	void 같은_멱등키로_사유가_다른_요청은_완료된_취소라도_충돌로_거절한다() {
		Payment payment = donePayment();
		storedCancel(payment, 3_000, 3_000, "단순 변심").complete("tx-1", Instant.now());

		assertThatThrownBy(() -> paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("중복 결제", 3_000)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.IDEMPOTENCY_KEY_CONFLICT);
	}

	@Test
	void 전액_취소로_시작한_멱등키에_금액을_지정해_재요청하면_충돌로_거절한다() {
		Payment payment = donePayment();
		storedCancel(payment, null, 15_000, "단순 변심").complete("tx-1", Instant.now());

		assertThatThrownBy(() -> paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", 15_000)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.IDEMPOTENCY_KEY_CONFLICT);
	}

	@Test
	void 새_취소_요청은_최초_요청_금액을_그대로_기록한다() {
		Payment payment = donePayment();
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.save(any(PaymentCancel.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 200L));

		paymentService.startCancel(10L, 100L, "key-1", new PaymentCancelRequest("단순 변심", null));

		ArgumentCaptor<PaymentCancel> saved = ArgumentCaptor.forClass(PaymentCancel.class);
		verify(paymentCancelRepository).save(saved.capture());
		assertThat(saved.getValue().getRequestedAmount()).isNull();
		assertThat(saved.getValue().getCancelAmount()).isEqualTo(15_000);
	}

	@Test
	void 함께_처리_중인_취소가_있으면_한_건이_완료되어도_주문을_전액_취소_처리하지_않는다() {
		Payment payment = donePayment();
		payment.reserveCancel(9_000);
		payment.reserveCancel(6_000);
		PaymentCancel first = withId(PaymentCancel.request(payment, "key-1", 9_000, 9_000, "단순 변심"), 200L);
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.findById(200L)).thenReturn(Optional.of(first));

		paymentService.completeCancel(100L, 200L, tossWithBalance(payment, 6_000));

		assertThat(first.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(payment.getBalanceAmount()).isEqualTo(6_000);
		assertThat(payment.getPendingCancelAmount()).isEqualTo(6_000);
		verify(orderService, never()).restoreCanceledOrder(any());
	}

	private PaymentCancel unknownCancel(Payment payment, int amount) {
		payment.reserveCancel(amount);
		PaymentCancel cancel = withId(PaymentCancel.request(payment, "key-1", amount, amount, "단순 변심"), 200L);
		cancel.markUnknown();
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
		when(paymentCancelRepository.findById(200L)).thenReturn(Optional.of(cancel));
		return cancel;
	}

	@Test
	void 거절된_재시도는_토스에_취소_흔적이_없을_때만_실패로_확정하고_예약을_한_번만_복구한다() {
		Payment payment = donePayment();
		PaymentCancel cancel = unknownCancel(payment, 3_000);

		PaymentCancelStatus first = paymentService.settleUnknownCancel(100L, 200L, 0, tossWithBalance(payment, 15_000));
		PaymentCancelStatus second = paymentService.settleUnknownCancel(100L, 200L, 0, tossWithBalance(payment, 15_000));

		assertThat(first).isEqualTo(PaymentCancelStatus.FAILED);
		assertThat(second).isEqualTo(PaymentCancelStatus.FAILED);
		assertThat(cancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
		assertThat(payment.getPendingCancelAmount()).isZero();
		assertThat(payment.getCancelableAmount()).isEqualTo(15_000);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
	}

	@Test
	void 거절된_재시도라도_토스_잔액에_이_취소가_반영돼_있으면_완료로_확정한다() {
		Payment payment = donePayment();
		PaymentCancel cancel = unknownCancel(payment, 3_000);

		PaymentCancelStatus status = paymentService.settleUnknownCancel(100L, 200L, 0, tossWithBalance(payment, 12_000));

		assertThat(status).isEqualTo(PaymentCancelStatus.DONE);
		assertThat(cancel.getTransactionKey()).isEqualTo("tx-1");
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(payment.getBalanceAmount()).isEqualTo(12_000);
		assertThat(payment.getPendingCancelAmount()).isZero();
	}

	@Test
	void 다른_취소가_함께_처리_중이면_거절된_재시도를_실패로_단정하지_않는다() {
		Payment payment = donePayment();
		PaymentCancel cancel = unknownCancel(payment, 3_000);
		payment.reserveCancel(2_000);

		PaymentCancelStatus status = paymentService.settleUnknownCancel(100L, 200L, 0, tossWithBalance(payment, 15_000));

		assertThat(status).isEqualTo(PaymentCancelStatus.UNKNOWN);
		assertThat(cancel.getStatus()).isEqualTo(PaymentCancelStatus.UNKNOWN);
		assertThat(payment.getPendingCancelAmount()).isEqualTo(5_000);
	}

	@Test
	void 취소_시작_이후_완료_누계가_바뀌었으면_거절된_재시도를_판단하지_않는다() {
		Payment payment = donePayment();
		PaymentCancel cancel = unknownCancel(payment, 3_000);

		PaymentCancelStatus status = paymentService.settleUnknownCancel(100L, 200L, 1_000, tossWithBalance(payment, 15_000));

		assertThat(status).isEqualTo(PaymentCancelStatus.UNKNOWN);
		assertThat(cancel.getStatus()).isEqualTo(PaymentCancelStatus.UNKNOWN);
		assertThat(payment.getPendingCancelAmount()).isEqualTo(3_000);
	}

	@Test
	void 주문_결제의_보상_취소를_기록하면_재고_복구를_맡기고_주문을_취소하며_보상_이력을_남긴다() {
		Payment payment = inProgress(readyPayment());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.recordCompensation(100L, canceledToss(payment));

		ArgumentCaptor<PaymentCancel> saved = ArgumentCaptor.forClass(PaymentCancel.class);
		verify(paymentCancelRepository).save(saved.capture());
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(payment.getBalanceAmount()).isZero();
		assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELED);
		verify(orderService).restoreCanceledOrder(payment.getOrder());
		assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(payment.getCompensationIdempotencyKey());
		assertThat(saved.getValue().getCancelAmount()).isEqualTo(15_000);
		assertThat(saved.getValue().getStatus()).isEqualTo(PaymentCancelStatus.DONE);
	}

	@Test
	void 이미_보상_취소가_반영된_결제는_다시_기록하지_않는다() {
		Payment payment = readyPayment();
		payment.approve("pk-1", "카드", Instant.now());
		payment.cancelFully();
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.recordCompensation(100L, canceledToss(payment));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		verify(paymentCancelRepository, never()).save(any());
		verifyNoInteractions(orderService, reservationService);
	}

	@Test
	void 보상_취소_응답이_취소_완료가_아니면_반영하지_않는다() {
		Payment payment = inProgress(readyPayment());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.recordCompensation(100L, doneToss(payment)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PG_RESPONSE_MISMATCH);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
		verify(paymentCancelRepository, never()).save(any());
	}

	@Test
	void 다른_요청이_이미_승인을_반영한_결제는_다시_확정하지_않는다() {
		Payment payment = reservationPayment();
		payment.approve("pk-1", "카드", Instant.now());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.completeApproval(100L, doneToss(payment));

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		verify(reservationService, never()).confirmPaid(any());
	}

	@Test
	void 정산할_수_없는_상태의_결제에_승인_응답이_오면_반영하지_않고_예외를_던진다() {
		Payment payment = reservationPayment();
		payment.expire();
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.completeApproval(100L, doneToss(payment)))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PAYMENT_NOT_CONFIRMABLE);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
		verify(reservationService, never()).confirmPaid(any());
	}

	@Test
	void 이미_확정된_결제는_실패나_결과_불명으로_덮어쓰지_않는다() {
		Payment payment = reservationPayment();
		payment.approve("pk-1", "카드", Instant.now());
		when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

		paymentService.fail(100L);
		paymentService.markUnknown(100L);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		verify(reservationService, never()).releaseUnpaid(any());
	}

	@Test
	void 승인을_시작하면_선점_시각을_함께_기록한다() {
		Payment payment = readyPayment();
		when(paymentRepository.findByTossOrderId(payment.getTossOrderId())).thenReturn(Optional.of(payment));
		when(paymentRepository.startProcessing(eq(100L), anyList(), eq(NOW))).thenReturn(1);

		paymentService.startApproval(10L, new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000));

		verify(paymentRepository).startProcessing(100L, List.of(PaymentStatus.READY, PaymentStatus.UNKNOWN), NOW);
	}

	@Test
	void 처리_제한_시간이_지난_결제만_복구_대상으로_조회한다() {
		when(paymentRepository.findStaleProcessingIds(NOW.minus(Payment.PROCESSING_TIMEOUT))).thenReturn(List.of(100L));

		assertThat(paymentService.findStaleProcessingIds()).containsExactly(100L);
	}

	@Test
	void 진행_중인_결제는_처리_제한_시간이_지나지_않았으면_대사를_거절한다() {
		Payment payment = inProgress(readyPayment());
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
		when(paymentRepository.startReconcile(100L, NOW.minus(Payment.PROCESSING_TIMEOUT), NOW)).thenReturn(0);

		assertThatThrownBy(() -> paymentService.startReconcile(10L, 100L))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PAYMENT_NOT_RECONCILABLE);
	}

	@Test
	void 자동_복구는_소유자_확인_없이_오래된_진행_중_결제를_선점한다() {
		Payment payment = inProgress(readyPayment());
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
		when(paymentRepository.startReconcile(100L, NOW.minus(Payment.PROCESSING_TIMEOUT), NOW)).thenReturn(1);

		assertThat(paymentService.startRecovery(100L)).isSameAs(payment);
	}

	@Test
	void 확정된_결제는_자동_복구_대상이_아니다() {
		when(paymentRepository.findById(100L)).thenReturn(Optional.of(donePayment()));

		assertThatThrownBy(() -> paymentService.startRecovery(100L))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PAYMENT_NOT_RECONCILABLE);
		verify(paymentRepository, never()).startReconcile(anyLong(), any(), any());
	}
}
