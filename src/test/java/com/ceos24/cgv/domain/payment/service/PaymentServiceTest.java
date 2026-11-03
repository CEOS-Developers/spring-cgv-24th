package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
}
