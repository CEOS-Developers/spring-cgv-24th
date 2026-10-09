package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.dto.ReservationCancelAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.global.client.TossCancelResult.Canceled;
import com.ceos24.cgv.global.client.TossCancelResult.Rejected;
import com.ceos24.cgv.global.client.TossCancelResult.Unknown;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 취소 T1 → 토스 취소 → T2를 차례로 잇기만 한다. @Transactional을 붙이지 않는 이유는 ReservationPaymentService와 같다.
// 승인과 순서가 반대다. 승인은 좌석을 먼저 확보하고 돈을 받지만, 취소는 환불이 확인된 뒤에 좌석을 놓는다.
// 어느 쪽이든 피하려는 것은 회원이 돈과 좌석을 둘 다 잃는 경우다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationCancelService {

    private final ReservationService reservationService;
    private final TossPaymentsClient tossPaymentsClient;

    // 결제 전 선점은 T1에서 바로 끝난다. 결제된 예매는 남은 좌석 전부를 환불한다.
    public void cancel(Long id, Long userId) {
        reservationService.startCancel(id, userId, null).ifPresent(this::refund);
    }

    public ReservationResponse cancelSeats(Long id, Long userId, ReservationCancelRequest req) {
        // 좌석을 고른 요청은 결제된 예매에서만 통과하므로 T1이 늘 취소 요청을 만든다
        ReservationCancelAttempt attempt = reservationService.startCancel(id, userId, req.seatKeys())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
        return refund(attempt);
    }

    public ReservationResponse retry(Long id, Long userId, Long paymentCancelId) {
        return refund(reservationService.startCancelRetry(id, userId, paymentCancelId));
    }

    private ReservationResponse refund(ReservationCancelAttempt attempt) {
        return switch (tossPaymentsClient.cancel(attempt.paymentKey(), attempt.idempotencyKey(),
                attempt.amount(), attempt.reason())) {
            case Canceled canceled -> complete(attempt, canceled);
            case Rejected rejected -> {
                fail(attempt, rejected);
                throw new CustomException(ErrorCode.PAYMENT_CANCEL_REJECTED);
            }
            // 좌석을 놓지 않는다. 실제로는 환불되지 않았는데 놓으면 회원은 돈과 좌석을 둘 다 잃는다.
            case Unknown unknown -> throw new CustomException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
        };
    }

    // 토스는 이미 환불했다. 여기서 나는 예외를 그대로 내보내면 500이 되어 회원은 환불이 안 된 줄 안다.
    // 취소가 IN_PROGRESS로 남으므로 같은 취소를 재시도하면 토스가 첫 응답을 돌려줘 그때 반영된다.
    private ReservationResponse complete(ReservationCancelAttempt attempt, Canceled canceled) {
        try {
            return reservationService.completeCancel(attempt, canceled.cancel(), canceled.payment().balanceAmount());
        } catch (RuntimeException e) {
            log.error("[Payment] 토스 취소 후 반영 실패 paymentCancelId={} transactionKey={}",
                    attempt.paymentCancelId(), canceled.cancel().transactionKey(), e);
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
        }
    }

    // 반영에 실패해도 회원에게 맞는 답은 "취소 거절"이다. 환불되지 않았고 좌석도 그대로다.
    // 취소가 IN_PROGRESS로 남아 새 취소를 막지만, 재시도하면 토스가 같은 거절을 돌려줘 그때 정리된다.
    private void fail(ReservationCancelAttempt attempt, Rejected rejected) {
        try {
            reservationService.failCancel(attempt, rejected.code(), rejected.message());
        } catch (RuntimeException e) {
            log.error("[Payment] 토스 취소 거절 후 반영 실패 paymentCancelId={}", attempt.paymentCancelId(), e);
        }
    }
}
