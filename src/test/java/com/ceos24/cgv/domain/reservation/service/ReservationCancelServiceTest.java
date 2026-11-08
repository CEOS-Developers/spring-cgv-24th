package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.dto.ReservationCancelAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRequest.SeatPosition;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
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
import java.util.Optional;

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

// 트랜잭션 경계 사이의 순서와 결과별 분기만 본다. 각 트랜잭션 안의 규칙은 컨트롤러·서비스 테스트가 본다.
@ExtendWith(MockitoExtension.class)
class ReservationCancelServiceTest {

    private static final ReservationCancelAttempt ATTEMPT = new ReservationCancelAttempt(
            70L, 1L, 2L, "tgen_test_key", "0123456789abcdef0123456789abcdef", 14000, "예매 부분 취소: A1");
    private static final ReservationCancelRequest SEATS = new ReservationCancelRequest(List.of(new SeatPosition(1, 1)));
    private static final TossCancelResult.Canceled CANCELED = TestFixtures.tossCanceled("tgen_test_key", 28000, 14000, 14000);

    @Mock ReservationService reservationService;
    @Mock TossPaymentsClient tossPaymentsClient;

    ReservationCancelService service;

    @BeforeEach
    void setUp() {
        service = new ReservationCancelService(reservationService, tossPaymentsClient);
    }

    @Test
    void 취소_요청이_커밋된_뒤_토스를_부르고_환불되면_좌석을_놓는다() {
        ReservationResponse cancelled = mock(ReservationResponse.class);
        given(reservationService.startCancel(1L, 2L, SEATS.seatKeys())).willReturn(Optional.of(ATTEMPT));
        given(tossPaymentsClient.cancel("tgen_test_key", ATTEMPT.idempotencyKey(), 14000, ATTEMPT.reason()))
                .willReturn(CANCELED);
        given(reservationService.completeCancel(ATTEMPT, CANCELED.cancel(), 14000)).willReturn(cancelled);

        assertThat(service.cancelSeats(1L, 2L, SEATS)).isSameAs(cancelled);

        InOrder order = inOrder(reservationService, tossPaymentsClient);
        order.verify(reservationService).startCancel(1L, 2L, SEATS.seatKeys());
        order.verify(tossPaymentsClient).cancel("tgen_test_key", ATTEMPT.idempotencyKey(), 14000, ATTEMPT.reason());
        order.verify(reservationService).completeCancel(ATTEMPT, CANCELED.cancel(), 14000);
    }

    @Test
    void 결제_전_선점은_토스를_부르지_않는다() {
        given(reservationService.startCancel(1L, 2L, null)).willReturn(Optional.empty());

        service.cancel(1L, 2L);

        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 취소_시작이_실패하면_토스를_부르지_않는다() {
        given(reservationService.startCancel(1L, 2L, SEATS.seatKeys()))
                .willThrow(new CustomException(ErrorCode.CANCEL_DEADLINE_PASSED));

        assertThatThrownBy(() -> service.cancelSeats(1L, 2L, SEATS))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCEL_DEADLINE_PASSED);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 거절되면_취소를_실패로_기록하고_409() {
        given(reservationService.startCancel(1L, 2L, SEATS.seatKeys())).willReturn(Optional.of(ATTEMPT));
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Rejected("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));

        assertThatThrownBy(() -> service.cancelSeats(1L, 2L, SEATS))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_REJECTED);
        verify(reservationService).failCancel(ATTEMPT, "NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다.");
        verify(reservationService, never()).completeCancel(any(), any(), anyInt());
    }

    @Test
    void 거절_반영이_실패해도_회원에게는_거절로_답한다() {
        // 환불되지 않았고 좌석도 그대로다. 취소는 진행 중으로 남고, 재시도하면 같은 거절이 와서 정리된다
        given(reservationService.startCancel(1L, 2L, SEATS.seatKeys())).willReturn(Optional.of(ATTEMPT));
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Rejected("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));
        willThrow(new DataAccessResourceFailureException("DB down"))
                .given(reservationService).failCancel(any(), anyString(), anyString());

        assertThatThrownBy(() -> service.cancelSeats(1L, 2L, SEATS))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_REJECTED);
    }

    @Test
    void 결과를_모르면_좌석을_건드리지_않고_504() {
        given(reservationService.startCancel(1L, 2L, SEATS.seatKeys())).willReturn(Optional.of(ATTEMPT));
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));

        assertThatThrownBy(() -> service.cancelSeats(1L, 2L, SEATS))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
        verify(reservationService, never()).completeCancel(any(), any(), anyInt());
        verify(reservationService, never()).failCancel(any(), any(), any());
    }

    @Test
    void 환불_뒤_반영이_실패하면_500이_아니라_결과_불명이다() {
        // 토스는 이미 환불했다. 500이면 회원은 환불이 안 된 줄 안다
        given(reservationService.startCancel(1L, 2L, SEATS.seatKeys())).willReturn(Optional.of(ATTEMPT));
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString())).willReturn(CANCELED);
        given(reservationService.completeCancel(any(), any(), anyInt()))
                .willThrow(new DataAccessResourceFailureException("DB down"));

        assertThatThrownBy(() -> service.cancelSeats(1L, 2L, SEATS))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
    }

    @Test
    void 재시도는_저장된_멱등키_금액_사유를_그대로_보낸다() {
        given(reservationService.startCancelRetry(1L, 2L, 70L)).willReturn(ATTEMPT);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString())).willReturn(CANCELED);

        service.retry(1L, 2L, 70L);

        verify(tossPaymentsClient).cancel("tgen_test_key", ATTEMPT.idempotencyKey(), 14000, ATTEMPT.reason());
        verify(reservationService).completeCancel(ATTEMPT, CANCELED.cancel(), 14000);
    }
}
