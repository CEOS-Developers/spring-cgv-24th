package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationPaymentAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 트랜잭션 경계 사이의 순서와 결과별 분기만 본다. 각 트랜잭션 안의 규칙은 ReservationServiceTest가 본다.
@ExtendWith(MockitoExtension.class)
class ReservationPaymentServiceTest {

    private static final PaymentConfirmRequest REQUEST =
            new PaymentConfirmRequest("tgen_test_key", "cgv-r-0123456789abcdef0123456789abcdef", 14000);
    private static final ReservationPaymentAttempt ATTEMPT = new ReservationPaymentAttempt(
            50L, 1L, 2L, REQUEST.paymentKey(), REQUEST.orderId(), 14000, LocalDateTime.of(2024, 6, 1, 9, 10));
    private static final TossPayment APPROVED = new TossPayment(
            REQUEST.paymentKey(), REQUEST.orderId(), "DONE", "카드", 14000, OffsetDateTime.now());

    @Mock ReservationService reservationService;
    @Mock TossPaymentsClient tossPaymentsClient;

    ReservationPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ReservationPaymentService(reservationService, tossPaymentsClient);
    }

    @Test
    void 승인_시작이_커밋된_뒤_토스를_부르고_승인되면_예매를_확정한다() {
        ReservationResponse confirmed = mock(ReservationResponse.class);
        given(reservationService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(REQUEST.paymentKey(), REQUEST.orderId(), 14000))
                .willReturn(new Approved(APPROVED));
        given(reservationService.completePayment(ATTEMPT, APPROVED)).willReturn(confirmed);

        assertThat(service.confirm(1L, 2L, REQUEST)).isSameAs(confirmed);

        InOrder order = inOrder(reservationService, tossPaymentsClient);
        order.verify(reservationService).startPayment(1L, 2L, REQUEST);
        order.verify(tossPaymentsClient).confirm(REQUEST.paymentKey(), REQUEST.orderId(), 14000);
        order.verify(reservationService).completePayment(ATTEMPT, APPROVED);
        verify(reservationService, never()).failPayment(any(), any(), any());
    }

    @Test
    void 토스에는_요청_본문이_아니라_T1이_확인한_금액을_보낸다() {
        ReservationPaymentAttempt attempt = new ReservationPaymentAttempt(
                50L, 1L, 2L, REQUEST.paymentKey(), REQUEST.orderId(), 28000, ATTEMPT.expiresAtBeforePayment());
        given(reservationService.startPayment(1L, 2L, REQUEST)).willReturn(attempt);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Unknown("timeout"));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST)).isInstanceOf(CustomException.class);

        verify(tossPaymentsClient).confirm(REQUEST.paymentKey(), REQUEST.orderId(), 28000);
    }

    @Test
    void 승인_시작에서_막히면_토스를_부르지_않는다() {
        given(reservationService.startPayment(1L, 2L, REQUEST))
                .willThrow(new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 거절되면_실패를_커밋한_뒤_PAYMENT_REJECTED() {
        given(reservationService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("REJECT_CARD_COMPANY", "카드사 거절"));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_REJECTED);
        verify(reservationService).failPayment(ATTEMPT, "REJECT_CARD_COMPANY", "카드사 거절");
        verify(reservationService, never()).completePayment(any(), any());
    }

    @Test
    void 결과를_모르면_어느_쪽으로도_확정하지_않는다() {
        given(reservationService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Unknown("timeout"));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        verify(reservationService, never()).completePayment(any(), any());
        verify(reservationService, never()).failPayment(any(), any(), any());
    }

    @Test
    void 토스_승인_후_반영에_실패하면_500이_아니라_결과_불명으로_알린다() {
        given(reservationService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Approved(APPROVED));
        given(reservationService.completePayment(ATTEMPT, APPROVED))
                .willThrow(new DataAccessResourceFailureException("connection lost"));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
    }
}
