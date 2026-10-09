package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.store.dto.PurchaseRefundAttempt;
import com.ceos24.cgv.domain.store.dto.PurchaseRefundRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.global.client.TossCancelResult;
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

import java.util.List;

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

// 트랜잭션 경계 사이의 순서와 결과별 분기만 본다. 각 트랜잭션 안의 규칙은 PurchaseAdminControllerTest가 본다.
@ExtendWith(MockitoExtension.class)
class PurchaseRefundServiceTest {

    private static final PurchaseRefundAttempt ATTEMPT = new PurchaseRefundAttempt(
            70L, 1L, "tgen_test_key", "0123456789abcdef0123456789abcdef", 5000, "매장 부분 환불: 팝콘×1");
    private static final PurchaseRefundRequest REQUEST =
            new PurchaseRefundRequest(List.of(new PurchaseRefundRequest.Item(10L, 1)));
    private static final TossCancelResult.Canceled CANCELED =
            TestFixtures.tossCanceled("tgen_test_key", 21000, 16000, 5000);

    @Mock PurchaseService purchaseService;
    @Mock TossPaymentsClient tossPaymentsClient;

    PurchaseRefundService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseRefundService(purchaseService, tossPaymentsClient);
    }

    @Test
    void 환불_요청이_커밋된_뒤_토스를_부르고_환불되면_수량을_반영한다() {
        PurchaseResponse refunded = mock(PurchaseResponse.class);
        given(purchaseService.startRefund(1L, REQUEST.items())).willReturn(ATTEMPT);
        given(tossPaymentsClient.cancel("tgen_test_key", ATTEMPT.idempotencyKey(), 5000, ATTEMPT.reason()))
                .willReturn(CANCELED);
        given(purchaseService.completeRefund(ATTEMPT, CANCELED.cancel(), 16000)).willReturn(refunded);

        assertThat(service.refundItems(1L, REQUEST)).isSameAs(refunded);

        InOrder order = inOrder(purchaseService, tossPaymentsClient);
        order.verify(purchaseService).startRefund(1L, REQUEST.items());
        order.verify(tossPaymentsClient).cancel("tgen_test_key", ATTEMPT.idempotencyKey(), 5000, ATTEMPT.reason());
        order.verify(purchaseService).completeRefund(ATTEMPT, CANCELED.cancel(), 16000);
    }

    @Test
    void 환불_시작이_실패하면_토스를_부르지_않는다() {
        given(purchaseService.startRefund(1L, null)).willThrow(new CustomException(ErrorCode.PURCHASE_NOT_PAID));

        assertThatThrownBy(() -> service.refundAll(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_NOT_PAID);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 거절되면_취소를_실패로_기록하고_반영이_실패해도_거절로_답한다() {
        given(purchaseService.startRefund(1L, REQUEST.items())).willReturn(ATTEMPT);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Rejected("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));
        willThrow(new DataAccessResourceFailureException("DB down"))
                .given(purchaseService).failRefund(any(), anyString(), anyString());

        assertThatThrownBy(() -> service.refundItems(1L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_REJECTED);
        verify(purchaseService).failRefund(ATTEMPT, "NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다.");
        verify(purchaseService, never()).completeRefund(any(), any(), anyInt());
    }

    @Test
    void 결과를_모르면_수량을_건드리지_않고_504() {
        given(purchaseService.startRefund(1L, REQUEST.items())).willReturn(ATTEMPT);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));

        assertThatThrownBy(() -> service.refundItems(1L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
        verify(purchaseService, never()).completeRefund(any(), any(), anyInt());
        verify(purchaseService, never()).failRefund(any(), any(), any());
    }

    @Test
    void 환불_뒤_반영이_실패하면_500이_아니라_결과_불명이다() {
        given(purchaseService.startRefund(1L, REQUEST.items())).willReturn(ATTEMPT);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString())).willReturn(CANCELED);
        given(purchaseService.completeRefund(any(), any(), anyInt()))
                .willThrow(new DataAccessResourceFailureException("DB down"));

        assertThatThrownBy(() -> service.refundItems(1L, REQUEST))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
    }
}
