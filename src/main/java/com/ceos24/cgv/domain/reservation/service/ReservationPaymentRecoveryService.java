package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.dto.PaymentRecoveryResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

// 결과를 모르는 예매 결제(IN_PROGRESS)를 토스 조회로 정리한다. 토스 승인 뒤 T2가 실패했거나, 응답이 끊겼거나,
// 서버가 그 사이 죽은 결제다. 회원은 504를 받았고 결제는 승인 중으로 남아 같은 예매의 새 결제·취소를 막고 있다.
// 조회 → 반영(예매 행 잠금) → 필요하면 토스 취소로 이어진다. 토스를 기다리는 동안 트랜잭션을 열어 두지 않으려고
// @Transactional을 붙이지 않는다(ReservationPaymentService와 같은 이유).
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationPaymentRecoveryService {

    private final ReservationService reservationService;
    private final ReservationCancelService reservationCancelService;
    private final TossPaymentsClient tossPaymentsClient;

    // 사람이 알아채기 전에 정리한다. 서버가 T2 전에 죽은 결제는 아무도 다시 요청하지 않는다.
    // 한 건이 실패해도 나머지는 계속한다. 실패한 건은 승인 중으로 남아 다음 회차에 다시 묻는다.
    @Scheduled(initialDelay = 60, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void recoverStale() {
        for (String orderId : reservationService.findStaleApprovalOrderIds()) {
            try {
                PaymentRecoveryResponse result = recover(orderId);
                log.info("[Recovery] 예매 결제 정리 orderId={} outcome={} detail={}",
                        orderId, result.outcome(), result.detail());
            } catch (RuntimeException e) {
                log.error("[Recovery] 예매 결제 정리 실패 orderId={}", orderId, e);
            }
        }
        for (ReservationCancelRetryTarget target : reservationService.findStaleCancelTargets()) {
            try {
                reservationCancelService.retry(target.reservationId(), target.userId(), target.paymentCancelId());
                log.info("[Recovery] 예매 취소 재시도 완료 paymentCancelId={}", target.paymentCancelId());
            } catch (RuntimeException e) {
                // 거절이면 취소가 FAILED로 끝났고, 결과 불명이면 다음 회차에 다시 보낸다.
                log.warn("[Recovery] 예매 취소 재시도 미완료 paymentCancelId={} reason={}",
                        target.paymentCancelId(), e.getMessage());
            }
        }
    }

    // 관리자 요청과 스케줄러가 함께 쓴다. 승인 시작 후 RECOVERY_DELAY가 지나지 않았으면 PAYMENT_IN_PROGRESS다.
    public PaymentRecoveryResponse recover(String orderId) {
        ReservationRecoveryTarget target = reservationService.findRecoveryTarget(orderId);
        return switch (tossPaymentsClient.findByPaymentKey(target.paymentKey())) {
            case Found found when found.payment().isDone() -> applyApproval(target, found.payment());
            case Found found when found.payment().isNeverApproved() ->
                    fail(target, "TOSS_" + found.payment().status(), "토스 조회 결과 승인되지 않음");
            // READY·IN_PROGRESS는 아직 승인될 수 있다. 인증 후 10분이 지나면 토스가 EXPIRED로 바꾼다.
            // 취소 상태는 누군가 토스에서 직접 취소한 경우라 자동으로 맞추지 않는다.
            case Found found -> unresolved(target, "토스 상태 " + found.payment().status());
            // paymentKey로 물었는데 없다. 토스가 발급한 적 없는 키라 승인됐을 수 없다.
            case NotFound notFound -> fail(target, notFound.code(), notFound.message());
            case Unavailable unavailable -> unresolved(target, unavailable.reason());
        };
    }

    private PaymentRecoveryResponse applyApproval(ReservationRecoveryTarget target, TossPayment approved) {
        // 금액이 다른 결제를 이 주문으로 확정하면 좌석 가격과 받은 돈이 어긋난다. 사람이 볼 일이다.
        if (!target.orderId().equals(approved.orderId()) || approved.totalAmount() != target.amount()) {
            log.error("[Recovery] 토스 승인 결제가 주문과 다름 orderId={} tossOrderId={} amount={} tossAmount={}",
                    target.orderId(), approved.orderId(), target.amount(), approved.totalAmount());
            return unresolved(target, "토스 승인 결제의 주문번호·금액이 우리 주문과 다름");
        }
        Optional<ReservationCancelAttempt> refund = reservationService.applyRecoveredApproval(target, approved);
        if (refund.isEmpty()) {
            return PaymentRecoveryResponse.of(target.orderId(), Outcome.APPROVED, "좌석이 남아 있어 예매 확정");
        }
        return refundLostSeats(target, refund.get());
    }

    // 승인은 이미 확정했고 환불 요청도 저장했다. 여기서 결과를 모르면 그 취소 요청을 스케줄러가 같은 멱등키로 다시 보낸다.
    private PaymentRecoveryResponse refundLostSeats(ReservationRecoveryTarget target, ReservationCancelAttempt attempt) {
        try {
            reservationCancelService.refund(attempt);
            return PaymentRecoveryResponse.of(target.orderId(), Outcome.REFUNDED, attempt.reason());
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_CANCEL_REJECTED) {
                // 돈은 빠졌고 좌석은 없는데 환불이 거절됐다. 자동으로 풀 방법이 없다.
                log.error("[Recovery] 좌석을 잃은 결제의 환불 거절 orderId={} paymentCancelId={}",
                        target.orderId(), attempt.paymentCancelId());
                return PaymentRecoveryResponse.of(target.orderId(), Outcome.REFUND_REJECTED, e.getMessage());
            }
            return PaymentRecoveryResponse.of(target.orderId(), Outcome.REFUND_PENDING, e.getMessage());
        }
    }

    private PaymentRecoveryResponse fail(ReservationRecoveryTarget target, String code, String message) {
        reservationService.failRecoveredPayment(target, code, message);
        return PaymentRecoveryResponse.of(target.orderId(), Outcome.FAILED, code);
    }

    private PaymentRecoveryResponse unresolved(ReservationRecoveryTarget target, String reason) {
        return PaymentRecoveryResponse.of(target.orderId(), Outcome.UNRESOLVED, reason);
    }
}
