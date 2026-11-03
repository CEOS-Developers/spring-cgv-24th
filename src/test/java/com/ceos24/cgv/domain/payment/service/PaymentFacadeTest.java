package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

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
}
