package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.dto.PaymentRecoveryResponse.Outcome;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRetryTarget;
import com.ceos24.cgv.domain.reservation.dto.ReservationRecoveryTarget;
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
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 토스 조회 결과별로 어느 쪽으로 정리하는지만 본다. 각 트랜잭션 안의 잠금·상태 규칙은 ReservationAdminControllerTest가 본다.
@ExtendWith(MockitoExtension.class)
class ReservationPaymentRecoveryServiceTest {

    private static final String ORDER_ID = "cgv-r-0123456789abcdef0123456789abcdef";
    private static final String PAYMENT_KEY = "tgen_test_key";
    private static final ReservationRecoveryTarget TARGET =
            new ReservationRecoveryTarget(50L, 1L, 2L, ORDER_ID, PAYMENT_KEY, 14000);
    private static final TossPayment APPROVED =
            TestFixtures.approvedToss(PAYMENT_KEY, ORDER_ID, 14000, OffsetDateTime.now());
    private static final ReservationCancelAttempt REFUND = new ReservationCancelAttempt(
            70L, 1L, 2L, PAYMENT_KEY, "idem-key", 14000, "좌석 선점 만료로 자동 환불");

    @Mock ReservationService reservationService;
    @Mock ReservationCancelService reservationCancelService;
    @Mock TossPaymentsClient tossPaymentsClient;

    ReservationPaymentRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new ReservationPaymentRecoveryService(reservationService, reservationCancelService, tossPaymentsClient);
    }

    @Test
    void 토스가_승인했고_좌석이_남아_있으면_확정한다() {
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(APPROVED));
        given(reservationService.applyRecoveredApproval(TARGET, APPROVED)).willReturn(Optional.empty());

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.APPROVED);
        verify(reservationCancelService, never()).refund(any());
    }

    @Test
    void 토스가_승인했지만_좌석을_잃었으면_환불한다() {
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(APPROVED));
        given(reservationService.applyRecoveredApproval(TARGET, APPROVED)).willReturn(Optional.of(REFUND));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.REFUNDED);
        verify(reservationCancelService).refund(REFUND);
    }

    @Test
    void 환불_결과를_모르면_재시도_대기로_남긴다() {
        // 승인 확정과 환불 요청은 이미 커밋됐다. 남은 취소 요청을 스케줄러가 같은 멱등키로 다시 보낸다
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(APPROVED));
        given(reservationService.applyRecoveredApproval(TARGET, APPROVED)).willReturn(Optional.of(REFUND));
        willThrow(new CustomException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN)).given(reservationCancelService).refund(REFUND);

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.REFUND_PENDING);
    }

    @Test
    void 환불이_거절되면_수동_확인_대상으로_알린다() {
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(APPROVED));
        given(reservationService.applyRecoveredApproval(TARGET, APPROVED)).willReturn(Optional.of(REFUND));
        willThrow(new CustomException(ErrorCode.PAYMENT_CANCEL_REJECTED)).given(reservationCancelService).refund(REFUND);

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.REFUND_REJECTED);
    }

    @Test
    void 토스_승인_결제의_금액이_주문과_다르면_확정하지_않는다() {
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY))
                .willReturn(new Found(TestFixtures.approvedToss(PAYMENT_KEY, ORDER_ID, 28000, OffsetDateTime.now())));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.UNRESOLVED);
        verify(reservationService, never()).applyRecoveredApproval(any(), any());
        verify(reservationService, never()).failRecoveredPayment(any(), any(), any());
    }

    @Test
    void 토스에서_승인_실패나_만료로_끝났으면_실패로_확정한다() {
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(tossWithStatus("EXPIRED")));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.FAILED);
        verify(reservationService).failRecoveredPayment(TARGET, "TOSS_EXPIRED", "토스 조회 결과 승인되지 않음");
    }

    @Test
    void 토스에_없는_paymentKey면_실패로_확정한다() {
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY))
                .willReturn(new NotFound("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.FAILED);
        verify(reservationService).failRecoveredPayment(TARGET, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.");
    }

    @Test
    void 토스에서_아직_승인될_수_있는_상태면_보류한다() {
        // 인증만 끝난 IN_PROGRESS는 우리 승인 요청이 늦게 닿으면 아직 DONE이 될 수 있다
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(tossWithStatus("IN_PROGRESS")));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.UNRESOLVED);
        verify(reservationService, never()).failRecoveredPayment(any(), any(), any());
    }

    @Test
    void 토스에_묻지_못하면_보류한다() {
        // 조회 실패를 "결제 없음"으로 읽으면 승인된 결제를 실패로 확정하게 된다
        given(reservationService.findRecoveryTarget(ORDER_ID)).willReturn(TARGET);
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Unavailable("Read timed out"));

        assertThat(service.recover(ORDER_ID).outcome()).isEqualTo(Outcome.UNRESOLVED);
        verify(reservationService, never()).failRecoveredPayment(any(), any(), any());
        verify(reservationService, never()).applyRecoveredApproval(any(), any());
    }

    @Test
    void 정리할_수_없는_결제면_토스에_묻지_않는다() {
        given(reservationService.findRecoveryTarget(ORDER_ID)).willThrow(new CustomException(ErrorCode.PAYMENT_IN_PROGRESS));

        assertThatThrownBy(() -> service.recover(ORDER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        verify(tossPaymentsClient, never()).findByPaymentKey(anyString());
    }

    @Test
    void 스케줄러는_한_건이_실패해도_나머지를_정리하고_결과_불명_취소도_다시_보낸다() {
        String other = "cgv-r-ffffffffffffffffffffffffffffffff";
        ReservationRecoveryTarget otherTarget = new ReservationRecoveryTarget(51L, 3L, 4L, other, "tgen_other", 14000);
        given(reservationService.findStaleApprovalOrderIds()).willReturn(List.of(ORDER_ID, other));
        given(reservationService.findRecoveryTarget(ORDER_ID))
                .willThrow(new DataAccessResourceFailureException("connection lost"));
        given(reservationService.findRecoveryTarget(other)).willReturn(otherTarget);
        given(tossPaymentsClient.findByPaymentKey("tgen_other"))
                .willReturn(new NotFound("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
        given(reservationService.findStaleCancelTargets()).willReturn(List.of(
                new ReservationCancelRetryTarget(1L, 2L, 70L), new ReservationCancelRetryTarget(3L, 4L, 71L)));
        willThrow(new CustomException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN))
                .given(reservationCancelService).retry(1L, 2L, 70L);

        service.recoverStale();

        verify(reservationService).failRecoveredPayment(otherTarget, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.");
        verify(reservationCancelService).retry(1L, 2L, 70L);
        verify(reservationCancelService).retry(3L, 4L, 71L);
    }

    private TossPayment tossWithStatus(String status) {
        return new TossPayment(PAYMENT_KEY, ORDER_ID, status, null, 14000, 14000, null, List.of());
    }
}
