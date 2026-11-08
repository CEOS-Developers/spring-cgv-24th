package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

	@Mock
	private PaymentService paymentService;
	@Mock
	private TossPaymentClient tossPaymentClient;

	@InjectMocks
	private PaymentFacade paymentFacade;

	private Payment readyPayment() {
		return withId(Payment.forOrder(order(1L, user(10L), theater(2L), 15_000)), 100L);
	}

	private TossPaymentResponse toss(Payment payment, String status) {
		return new TossPaymentResponse("pk-1", payment.getTossOrderId(), status, 15_000, 15_000, "카드", OffsetDateTime.now(), null);
	}

	@Test
	void 승인_후_주문_확정에_실패하면_멱등키로_전액_취소하고_보상_결과를_알린다() {
		Payment payment = readyPayment();
		TossPaymentResponse approved = toss(payment, "DONE");
		TossPaymentResponse canceled = toss(payment, "CANCELED");
		PaymentConfirmRequest request = new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000);
		when(paymentService.startApproval(10L, request)).thenReturn(payment);
		when(tossPaymentClient.confirm(anyString(), anyString(), anyInt(), anyString())).thenReturn(approved);
		when(paymentService.completeApproval(100L, approved)).thenThrow(new RuntimeException("재고 부족"));
		when(tossPaymentClient.cancel(eq("pk-1"), anyString(), isNull(), anyString())).thenReturn(canceled);

		assertThatThrownBy(() -> paymentFacade.confirm(10L, request))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PAYMENT_COMPENSATED);
		verify(tossPaymentClient).cancel(eq("pk-1"), anyString(), isNull(), eq("compensate-" + payment.getTossOrderId()));
		verify(paymentService).recordCompensation(100L, canceled);
		verify(paymentService, never()).markUnknown(anyLong());
	}

	@Test
	void 보상_취소도_실패하면_결과_불명으로_표시하고_원래_예외를_던진다() {
		Payment payment = readyPayment();
		TossPaymentResponse approved = toss(payment, "DONE");
		PaymentConfirmRequest request = new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000);
		RuntimeException original = new RuntimeException("재고 부족");
		when(paymentService.startApproval(10L, request)).thenReturn(payment);
		when(tossPaymentClient.confirm(anyString(), anyString(), anyInt(), anyString())).thenReturn(approved);
		when(paymentService.completeApproval(100L, approved)).thenThrow(original);
		when(tossPaymentClient.cancel(anyString(), anyString(), isNull(), anyString()))
				.thenThrow(TossPaymentException.unknown("timeout"));

		assertThatThrownBy(() -> paymentFacade.confirm(10L, request)).isSameAs(original);
		verify(paymentService).markUnknown(100L);
		verify(paymentService, never()).recordCompensation(anyLong(), any());
	}

	@Test
	void 대사에서_토스가_DONE이면_승인_확정_흐름을_다시_수행한다() {
		Payment payment = readyPayment();
		TossPaymentResponse approved = toss(payment, "DONE");
		PaymentResponse response = PaymentResponse.from(payment, List.of());
		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenReturn(approved);
		when(paymentService.completeApproval(100L, approved)).thenReturn(response);

		PaymentResponse result = paymentFacade.reconcile(10L, 100L);

		assertThat(result).isSameAs(response);
		verify(paymentService).completeApproval(100L, approved);
		verify(tossPaymentClient, never()).cancel(anyString(), anyString(), any(), anyString());
	}

	@Test
	void 대사에서_토스에_승인된_결제가_없으면_실패_처리한다() {
		Payment payment = readyPayment();
		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
				.thenThrow(TossPaymentException.rejected("NOT_FOUND_PAYMENT", "존재하지 않는 결제입니다."));

		paymentFacade.reconcile(10L, 100L);

		verify(paymentService).fail(100L);
		verify(paymentService, never()).completeApproval(anyLong(), any());
	}

	@Test
	void 대사에서_결제_없음이_아닌_4xx는_실패로_단정하지_않고_결과_불명으로_둔다() {
		Payment payment = readyPayment();
		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
				.thenThrow(TossPaymentException.rejected("UNAUTHORIZED_KEY", "인증되지 않은 키입니다."));

		assertThatThrownBy(() -> paymentFacade.reconcile(10L, 100L)).isInstanceOf(TossPaymentException.class);
		verify(paymentService).markUnknown(100L);
		verify(paymentService, never()).fail(anyLong());
	}

	private PaymentService.CancelStart cancelStart(Payment payment) {
		payment.approve("pk-1", "카드", Instant.now());
		PaymentCancel cancel = withId(PaymentCancel.request(payment, "key-1", 15_000, "단순 변심"), 200L);
		return PaymentService.CancelStart.toCall(payment, cancel);
	}

	@Test
	void 토스_취소가_성공하면_완료_반영_결과를_반환한다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", null);
		TossPaymentResponse canceled = toss(payment, "CANCELED");
		PaymentResponse response = PaymentResponse.from(payment, List.of());
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(cancelStart(payment));
		when(tossPaymentClient.cancel("pk-1", "단순 변심", 15_000, "key-1")).thenReturn(canceled);
		when(paymentService.completeCancel(100L, 200L, canceled)).thenReturn(response);

		PaymentResponse result = paymentFacade.cancel(10L, 100L, "key-1", request);

		assertThat(result).isSameAs(response);
		verify(paymentService, never()).failCancel(anyLong(), anyLong());
		verify(paymentService, never()).markCancelUnknown(anyLong());
	}

	@Test
	void 이미_완료된_취소_요청은_토스를_호출하지_않고_그대로_반환한다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", null);
		PaymentResponse response = PaymentResponse.from(payment, List.of());
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(PaymentService.CancelStart.completed(response));

		assertThat(paymentFacade.cancel(10L, 100L, "key-1", request)).isSameAs(response);
		verifyNoInteractions(tossPaymentClient);
	}

	@Test
	void 토스가_취소를_거절하면_실패로_반영해_예약을_되돌리고_예외를_던진다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", null);
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(cancelStart(payment));
		when(tossPaymentClient.cancel(anyString(), anyString(), anyInt(), anyString()))
				.thenThrow(TossPaymentException.rejected("ALREADY_CANCELED_PAYMENT", "이미 취소된 결제입니다."));

		assertThatThrownBy(() -> paymentFacade.cancel(10L, 100L, "key-1", request)).isInstanceOf(TossPaymentException.class);
		verify(paymentService).failCancel(100L, 200L);
		verify(paymentService, never()).markCancelUnknown(anyLong());
		verify(paymentService, never()).completeCancel(anyLong(), anyLong(), any());
	}

	@Test
	void 토스_취소_결과가_불명이면_UNKNOWN으로_반영하고_예약은_유지한다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", null);
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(cancelStart(payment));
		when(tossPaymentClient.cancel(anyString(), anyString(), anyInt(), anyString()))
				.thenThrow(TossPaymentException.unknown("timeout"));

		assertThatThrownBy(() -> paymentFacade.cancel(10L, 100L, "key-1", request)).isInstanceOf(TossPaymentException.class);
		verify(paymentService).markCancelUnknown(200L);
		verify(paymentService, never()).failCancel(anyLong(), anyLong());
	}

	@Test
	void 대사에서_토스_결과도_불명이면_결과_불명을_유지하고_예외를_던진다() {
		Payment payment = readyPayment();
		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenThrow(TossPaymentException.unknown("timeout"));

		assertThatThrownBy(() -> paymentFacade.reconcile(10L, 100L)).isInstanceOf(TossPaymentException.class);
		verify(paymentService).markUnknown(100L);
		verify(paymentService, never()).fail(anyLong());
	}
}
