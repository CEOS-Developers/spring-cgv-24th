package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationPaymentAttempt;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 승인 T1 → 토스 호출 → T2를 차례로 잇기만 한다. 트랜잭션은 ReservationService의 메서드가 각각 연다.
// 이 클래스에는 @Transactional을 붙이지 않는다(readOnly 포함). 붙이면 T1·T2가 바깥 트랜잭션에 합류해
// 토스 응답을 기다리는 최대 1분 동안 예매 행 락과 DB 커넥션을 쥐고, readOnly면 쓰기가 반영되지 않을 수도 있다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationPaymentService {

    private final ReservationService reservationService;
    private final TossPaymentsClient tossPaymentsClient;

    public ReservationResponse confirm(Long id, Long userId, PaymentConfirmRequest req) {
        ReservationPaymentAttempt attempt = reservationService.startPayment(id, userId, req);

        return switch (tossPaymentsClient.confirm(attempt.paymentKey(), attempt.orderId(), attempt.amount())) {
            case Approved approved -> complete(attempt, approved);
            case Rejected rejected -> {
                reservationService.failPayment(attempt, rejected.code(), rejected.message());
                throw new CustomException(rejected.serviceError()
                        ? ErrorCode.PAYMENT_SERVICE_UNAVAILABLE : ErrorCode.PAYMENT_REJECTED);
            }
            // 결제를 IN_PROGRESS로 둔다. 실패로 확정하면 돈이 빠진 회원이 다시 결제할 수 있다.
            case Unknown unknown -> throw new CustomException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        };
    }

    // 토스는 이미 승인했다. 여기서 나는 예외를 그대로 내보내면 500이 되어 회원은 결제가 안 된 줄 안다.
    private ReservationResponse complete(ReservationPaymentAttempt attempt, Approved approved) {
        try {
            return reservationService.completePayment(attempt, approved.payment());
        } catch (RuntimeException e) {
            log.error("[Payment] 토스 승인 후 반영 실패 orderId={} paymentKey={}",
                    attempt.orderId(), approved.payment().paymentKey(), e);
            throw new CustomException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        }
    }
}
