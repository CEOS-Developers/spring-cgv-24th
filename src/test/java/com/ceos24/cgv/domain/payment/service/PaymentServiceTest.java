package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
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
}
