package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.payment.dto.PaymentRecoveryResponse.Outcome;
import com.ceos24.cgv.domain.store.dto.PurchaseRecoveryTarget;
import com.ceos24.cgv.domain.store.dto.PurchaseRefundRetryTarget;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.NotFound;
import com.ceos24.cgv.global.client.TossLookupResult.Unavailable;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 토스 조회 결과별로 어느 쪽으로 정리하는지만 본다. 잠금·재고 복구는 PurchaseRecoveryControllerTest가 본다.
@ExtendWith(MockitoExtension.class)
class PurchasePaymentRecoveryServiceTest {

    private static final String ORDER_ID = "cgv-p-0123456789abcdef0123456789abcdef";
    private static final String PAYMENT_KEY = "tgen_test_key";
    private static final PurchaseRecoveryTarget TARGET = new PurchaseRecoveryTarget(50L, 1L, ORDER_ID, PAYMENT_KEY, 21000);

    @Mock PurchaseService purchaseService;
    @Mock PurchaseRefundService purchaseRefundService;
    @Mock TossPaymentsClient tossPaymentsClient;

    PurchasePaymentRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PurchasePaymentRecoveryService(purchaseService, purchaseRefundService, tossPaymentsClient);
    }

    @Test
    void 토스가_승인했으면_구매를_확정한다() {
        TossPayment approved = TestFixtures.approvedToss(PAYMENT_KEY, ORDER_ID, 21000, OffsetDateTime.now());
        given(purchaseService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(approved));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.APPROVED);
        verify(purchaseService).applyRecoveredApproval(TARGET, approved);
    }

    @Test
    void 토스_승인_결제의_주문번호가_다르면_확정하지_않는다() {
        given(purchaseService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(
                TestFixtures.approvedToss(PAYMENT_KEY, "cgv-p-other", 21000, OffsetDateTime.now())));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.UNRESOLVED);
        verify(purchaseService, never()).applyRecoveredApproval(any(), any());
    }

    @Test
    void 토스에서_승인_실패로_끝났으면_실패로_확정한다() {
        given(purchaseService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(
                new TossPayment(PAYMENT_KEY, ORDER_ID, "ABORTED", null, 21000, 21000, null, List.of())));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.FAILED);
        verify(purchaseService).failRecoveredPayment(TARGET, "TOSS_ABORTED", "토스 조회 결과 승인되지 않음");
    }

    @Test
    void 토스에_없는_paymentKey면_실패로_확정한다() {
        given(purchaseService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY))
                .willReturn(new NotFound("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    void 토스에_묻지_못하면_재고를_묶어_둔_채_보류한다() {
        // 되돌린 뒤 승인된 것으로 밝혀지면 판 수량보다 재고가 많아진다
        given(purchaseService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Unavailable("Read timed out"));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.UNRESOLVED);
        verify(purchaseService, never()).failRecoveredPayment(any(), any(), any());
    }

    @Test
    void 스케줄러는_한_건이_실패해도_나머지를_정리하고_결과_불명_환불도_다시_보낸다() {
        String other = "cgv-p-ffffffffffffffffffffffffffffffff";
        PurchaseRecoveryTarget otherTarget = new PurchaseRecoveryTarget(51L, 2L, other, "tgen_other", 5000);
        given(purchaseService.findStaleApprovalOrderIds()).willReturn(List.of(ORDER_ID, other));
        given(purchaseService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY))
                .willReturn(new NotFound("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        // 재고 복구가 락 대기에 걸렸다. 결제는 승인 중으로 남아 다음 회차에 다시 정리된다
        willThrow(new CannotAcquireLockException("lock wait timeout"))
                .given(purchaseService).failRecoveredPayment(any(), any(), any());
        given(purchaseService.findRecoveryTarget(other)).willReturn(otherTarget);
        given(tossPaymentsClient.findByPaymentKey("tgen_other")).willReturn(new Unavailable("timeout"));
        given(purchaseService.findStaleRefundTargets()).willReturn(List.of(new PurchaseRefundRetryTarget(3L, 70L)));
        willThrow(new CustomException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN))
                .given(purchaseRefundService).retry(3L, 70L);

        service.recoverStale();

        verify(tossPaymentsClient).findByPaymentKey("tgen_other");
        verify(purchaseRefundService).retry(3L, 70L);
    }
}
