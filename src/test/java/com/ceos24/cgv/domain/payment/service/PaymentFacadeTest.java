package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

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
		PaymentCancel cancel = withId(PaymentCancel.request(payment, "key-1", null, 15_000, "단순 변심"), 200L);
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

	@Test
	void 보상_취소_성공_후_기록에_실패하면_결과_불명으로_두고_대사에서_토스_취소_완료를_보고_복구한다() {
		Payment payment = readyPayment();
		TossPaymentResponse approved = toss(payment, "DONE");
		TossPaymentResponse canceled = toss(payment, "CANCELED");
		PaymentResponse recovered = PaymentResponse.from(payment, List.of());
		RuntimeException original = new RuntimeException("재고 부족");
		PaymentConfirmRequest request = new PaymentConfirmRequest("pk-1", payment.getTossOrderId(), 15_000);
		when(paymentService.startApproval(10L, request)).thenReturn(payment);
		when(tossPaymentClient.confirm(anyString(), anyString(), anyInt(), anyString())).thenReturn(approved);
		when(paymentService.completeApproval(100L, approved)).thenThrow(original);
		when(tossPaymentClient.cancel(eq("pk-1"), anyString(), isNull(), anyString())).thenReturn(canceled);
		when(paymentService.recordCompensation(100L, canceled))
				.thenThrow(new DataAccessResourceFailureException("DB 장애"))
				.thenReturn(recovered);

		assertThatThrownBy(() -> paymentFacade.confirm(10L, request)).isSameAs(original);
		verify(paymentService).markUnknown(100L);

		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenReturn(canceled);

		assertThat(paymentFacade.reconcile(10L, 100L)).isSameAs(recovered);
		verify(tossPaymentClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
		verify(paymentService, times(1)).completeApproval(anyLong(), any());
		verify(paymentService, never()).fail(anyLong());
	}

	@Test
	void 대사_중_보상_취소_기록이_실패하면_결과_불명으로_남겨_다시_확인하게_한다() {
		Payment payment = readyPayment();
		TossPaymentResponse canceled = toss(payment, "CANCELED");
		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenReturn(canceled);
		when(paymentService.recordCompensation(100L, canceled)).thenThrow(new DataAccessResourceFailureException("DB 장애"));

		assertThatThrownBy(() -> paymentFacade.reconcile(10L, 100L)).isInstanceOf(DataAccessResourceFailureException.class);
		verify(paymentService).markUnknown(100L);
	}

	@Test
	void 대사에서_토스_승인이_만료되거나_실패했으면_실패_처리한다() {
		Payment payment = readyPayment();
		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId()))
				.thenReturn(toss(payment, "EXPIRED"), toss(payment, "ABORTED"));

		paymentFacade.reconcile(10L, 100L);
		paymentFacade.reconcile(10L, 100L);

		verify(paymentService, times(2)).fail(100L);
		verify(paymentService, never()).markUnknown(anyLong());
		verify(paymentService, never()).completeApproval(anyLong(), any());
	}

	@Test
	void 대사에서_토스가_아직_승인_전이면_재승인하지_않고_결과_불명을_유지한다() {
		Payment payment = readyPayment();
		when(paymentService.startReconcile(10L, 100L)).thenReturn(payment);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenReturn(toss(payment, "IN_PROGRESS"));

		assertThatThrownBy(() -> paymentFacade.reconcile(10L, 100L))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PG_RESULT_UNKNOWN);
		verify(paymentService).markUnknown(100L);
		verify(paymentService, never()).fail(anyLong());
		verify(tossPaymentClient, never()).confirm(anyString(), anyString(), anyInt(), anyString());
	}

	@Test
	void 오래_멈춘_결제를_복구하며_한_건이_실패해도_나머지를_계속_처리한다() {
		Payment stuck = readyPayment();
		Payment other = withId(Payment.forOrder(order(2L, user(10L), theater(2L), 15_000)), 101L);
		TossPaymentResponse approved = toss(other, "DONE");
		when(paymentService.findStaleProcessingIds()).thenReturn(List.of(100L, 101L));
		when(paymentService.startRecovery(100L)).thenReturn(stuck);
		when(tossPaymentClient.getByOrderId(stuck.getTossOrderId())).thenThrow(TossPaymentException.unknown("timeout"));
		when(paymentService.startRecovery(101L)).thenReturn(other);
		when(tossPaymentClient.getByOrderId(other.getTossOrderId())).thenReturn(approved);

		paymentFacade.recoverStalePayments();

		verify(paymentService).markUnknown(100L);
		verify(paymentService).completeApproval(101L, approved);
	}

	private PaymentService.CancelStart unknownCancelStart(Payment payment) {
		payment.approve("pk-1", "카드", Instant.now());
		PaymentCancel cancel = withId(PaymentCancel.request(payment, "key-1", 3_000, 3_000, "단순 변심"), 200L);
		cancel.markUnknown();
		return PaymentService.CancelStart.toCall(payment, cancel);
	}

	@Test
	void 결과_불명_취소_재시도가_거절되면_토스_상태를_조회해_실패를_확정한다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", 3_000);
		TossPaymentException rejection = TossPaymentException.rejected("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액입니다.");
		TossPaymentResponse current = toss(payment, "DONE");
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(unknownCancelStart(payment));
		when(tossPaymentClient.cancel("pk-1", "단순 변심", 3_000, "key-1")).thenThrow(rejection);
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenReturn(current);
		when(paymentService.settleUnknownCancel(100L, 200L, 0, current)).thenReturn(PaymentCancelStatus.FAILED);

		assertThatThrownBy(() -> paymentFacade.cancel(10L, 100L, "key-1", request)).isSameAs(rejection);
		verify(paymentService, never()).failCancel(anyLong(), anyLong());
	}

	@Test
	void 결과_불명_취소_재시도가_거절됐지만_토스에_이미_반영돼_있으면_성공으로_응답한다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", 3_000);
		TossPaymentResponse current = new TossPaymentResponse("pk-1", payment.getTossOrderId(), "PARTIAL_CANCELED", 15_000,
				12_000, "카드", OffsetDateTime.now(), null);
		PaymentResponse response = PaymentResponse.from(payment, List.of());
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(unknownCancelStart(payment));
		when(tossPaymentClient.cancel("pk-1", "단순 변심", 3_000, "key-1"))
				.thenThrow(TossPaymentException.rejected("ALREADY_CANCELED_PAYMENT", "이미 취소된 결제입니다."));
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenReturn(current);
		when(paymentService.settleUnknownCancel(100L, 200L, 0, current)).thenReturn(PaymentCancelStatus.DONE);
		when(paymentService.getPayment(10L, 100L)).thenReturn(response);

		assertThat(paymentFacade.cancel(10L, 100L, "key-1", request)).isSameAs(response);
		verify(paymentService, never()).failCancel(anyLong(), anyLong());
	}

	@Test
	void 결과_불명_취소_재시도_거절_후_토스_조회도_실패하면_판단하지_않고_결과_불명으로_응답한다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", 3_000);
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(unknownCancelStart(payment));
		when(tossPaymentClient.cancel("pk-1", "단순 변심", 3_000, "key-1"))
				.thenThrow(TossPaymentException.rejected("UNAUTHORIZED_KEY", "인증되지 않은 키입니다."));
		when(tossPaymentClient.getByOrderId(payment.getTossOrderId())).thenThrow(TossPaymentException.unknown("timeout"));

		assertThatThrownBy(() -> paymentFacade.cancel(10L, 100L, "key-1", request))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.PG_RESULT_UNKNOWN);
		verify(paymentService, never()).settleUnknownCancel(anyLong(), anyLong(), anyInt(), any());
		verify(paymentService, never()).failCancel(anyLong(), anyLong());
	}

	@Test
	void 첫_취소_요청이_거절되면_토스를_다시_조회하지_않고_실패로_반영한다() {
		Payment payment = readyPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("단순 변심", null);
		when(paymentService.startCancel(10L, 100L, "key-1", request)).thenReturn(cancelStart(payment));
		when(tossPaymentClient.cancel(anyString(), anyString(), anyInt(), anyString()))
				.thenThrow(TossPaymentException.rejected("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제입니다."));

		assertThatThrownBy(() -> paymentFacade.cancel(10L, 100L, "key-1", request)).isInstanceOf(TossPaymentException.class);
		verify(paymentService).failCancel(100L, 200L);
		verify(tossPaymentClient, never()).getByOrderId(anyString());
	}
}
