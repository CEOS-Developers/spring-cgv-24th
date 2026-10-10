package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.store.dto.PurchasePaymentAttempt;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 트랜잭션 경계 사이의 순서와 결과별 분기만 본다. 재고 차감·복구 자체는 PurchaseRollbackTest가 실제 트랜잭션으로 본다.
@ExtendWith(MockitoExtension.class)
class PurchasePaymentServiceTest {

    private static final PaymentConfirmRequest REQUEST =
            new PaymentConfirmRequest("tgen_test_key", "cgv-p-0123456789abcdef0123456789abcdef", 13000);
    private static final PurchasePaymentAttempt ATTEMPT =
            new PurchasePaymentAttempt(50L, 1L, REQUEST.paymentKey(), REQUEST.orderId(), 13000);
    private static final TossPayment APPROVED = TestFixtures.approvedToss(
            REQUEST.paymentKey(), REQUEST.orderId(), 13000, OffsetDateTime.now());

    @Mock PurchaseService purchaseService;
    @Mock TossPaymentsClient tossPaymentsClient;

    PurchasePaymentService service;

    @BeforeEach
    void setUp() {
        service = new PurchasePaymentService(purchaseService, tossPaymentsClient);
    }

    @Test
    void 재고_차감이_커밋된_뒤_토스를_부르고_승인되면_구매를_확정한다() {
        PurchaseResponse paid = mock(PurchaseResponse.class);
        given(purchaseService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(REQUEST.paymentKey(), REQUEST.orderId(), 13000))
                .willReturn(new Approved(APPROVED));
        given(purchaseService.completePayment(ATTEMPT, APPROVED)).willReturn(paid);

        assertThat(service.confirm(1L, 2L, REQUEST)).isSameAs(paid);

        InOrder order = inOrder(purchaseService, tossPaymentsClient);
        order.verify(purchaseService).startPayment(1L, 2L, REQUEST);
        order.verify(tossPaymentsClient).confirm(REQUEST.paymentKey(), REQUEST.orderId(), 13000);
        order.verify(purchaseService).completePayment(ATTEMPT, APPROVED);
    }

    @Test
    void 승인_직전에_품절이면_토스를_부르지_않는다() {
        given(purchaseService.startPayment(1L, 2L, REQUEST)).willThrow(new CustomException(ErrorCode.OUT_OF_STOCK));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 거절되면_재고를_되돌린_뒤_PAYMENT_REJECTED() {
        given(purchaseService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("REJECT_CARD_COMPANY", "카드사 거절", false));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_REJECTED);
        verify(purchaseService).failPayment(ATTEMPT, "REJECT_CARD_COMPANY", "카드사 거절");
        verify(purchaseService, never()).completePayment(any(), any());
    }

    @Test
    void 토스가_판단하지_않은_거절도_재고를_되돌리고_결제_서비스_장애로_알린다() {
        given(purchaseService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("CONNECTION_FAILED", "Connection refused", true));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        verify(purchaseService).failPayment(ATTEMPT, "CONNECTION_FAILED", "Connection refused");
    }

    @Test
    void 거절_후_재고_복구가_실패해도_회원에게는_거절로_알린다() {
        // 돈은 빠지지 않았다. 락 대기 같은 복구 실패를 500이나 "주문이 몰림"으로 알리면 결제 결과를 오해한다
        given(purchaseService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("REJECT_CARD_COMPANY", "카드사 거절", false));
        willThrow(new CustomException(ErrorCode.STOCK_LOCK_CONFLICT))
                .given(purchaseService).failPayment(any(), any(), any());

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_REJECTED);
    }

    @Test
    void 결과를_모르면_재고를_되돌리지_않는다() {
        given(purchaseService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Unknown("timeout"));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        verify(purchaseService, never()).failPayment(any(), any(), any());
        verify(purchaseService, never()).completePayment(any(), any());
    }

    @Test
    void 토스_승인_후_반영에_실패하면_500이_아니라_결과_불명으로_알린다() {
        given(purchaseService.startPayment(1L, 2L, REQUEST)).willReturn(ATTEMPT);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Approved(APPROVED));
        given(purchaseService.completePayment(ATTEMPT, APPROVED))
                .willThrow(new DataAccessResourceFailureException("connection lost"));

        assertThatThrownBy(() -> service.confirm(1L, 2L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
    }
}
