package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.payment.dto.PaymentRecoveryResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentRecoveryResponse.Outcome;
import com.ceos24.cgv.domain.store.dto.PurchaseRecoveryTarget;
import com.ceos24.cgv.domain.store.dto.PurchaseRefundRetryTarget;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.NotFound;
import com.ceos24.cgv.global.client.TossLookupResult.Unavailable;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

// 결과를 모르는 매점 결제(IN_PROGRESS)를 토스 조회로 정리한다. 그동안 T1이 차감한 재고는 차감된 채로 묶여 있다.
// 예매와 달리 잃을 자원이 없어 승인이면 확정, 미승인이면 실패 + 재고 복구 둘 중 하나다.
// 토스를 기다리는 동안 트랜잭션을 열어 두지 않으려고 @Transactional을 붙이지 않는다(PurchasePaymentService와 같은 이유).
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePaymentRecoveryService {

    private final PurchaseService purchaseService;
    private final PurchaseRefundService purchaseRefundService;
    private final TossPaymentsClient tossPaymentsClient;

    // 한 건이 실패해도 나머지는 계속한다. 실패한 건은 승인 중으로 남아 다음 회차에 다시 묻는다.
    @Scheduled(initialDelay = 60, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void recoverStale() {
        for (String orderId : purchaseService.findStaleApprovalOrderIds()) {
            try {
                PaymentRecoveryResponse result = recover(orderId);
                log.info("[Recovery] 매점 결제 정리 orderId={} outcome={} detail={}",
                        orderId, result.outcome(), result.detail());
            } catch (RuntimeException e) {
                log.error("[Recovery] 매점 결제 정리 실패 orderId={}", orderId, e);
            }
        }
        for (PurchaseRefundRetryTarget target : purchaseService.findStaleRefundTargets()) {
            try {
                purchaseRefundService.retry(target.purchaseId(), target.paymentCancelId());
                log.info("[Recovery] 매점 환불 재시도 완료 paymentCancelId={}", target.paymentCancelId());
            } catch (RuntimeException e) {
                log.warn("[Recovery] 매점 환불 재시도 미완료 paymentCancelId={} reason={}",
                        target.paymentCancelId(), e.getMessage());
            }
        }
    }

    // 관리자 요청과 스케줄러가 함께 쓴다. 분류 기준은 ReservationPaymentRecoveryService.recover와 같다.
    public PaymentRecoveryResponse recover(String orderId) {
        PurchaseRecoveryTarget target = purchaseService.findRecoveryTarget(orderId);
        return switch (tossPaymentsClient.findByPaymentKey(target.paymentKey())) {
            case Found found when found.payment().isDone() -> applyApproval(target, found.payment());
            case Found found when found.payment().isNeverApproved() ->
                    fail(target, "TOSS_" + found.payment().status(), "토스 조회 결과 승인되지 않음");
            case Found found -> unresolved(target, "토스 상태 " + found.payment().status());
            case NotFound notFound -> fail(target, notFound.code(), notFound.message());
            case Unavailable unavailable -> unresolved(target, unavailable.reason());
        };
    }

    private PaymentRecoveryResponse applyApproval(PurchaseRecoveryTarget target, TossPayment approved) {
        if (!target.orderId().equals(approved.orderId()) || approved.totalAmount() != target.amount()) {
            log.error("[Recovery] 토스 승인 결제가 주문과 다름 orderId={} tossOrderId={} amount={} tossAmount={}",
                    target.orderId(), approved.orderId(), target.amount(), approved.totalAmount());
            return unresolved(target, "토스 승인 결제의 주문번호·금액이 우리 주문과 다름");
        }
        purchaseService.applyRecoveredApproval(target, approved);
        return PaymentRecoveryResponse.of(target.orderId(), Outcome.APPROVED, "구매 확정");
    }

    private PaymentRecoveryResponse fail(PurchaseRecoveryTarget target, String code, String message) {
        purchaseService.failRecoveredPayment(target, code, message);
        return PaymentRecoveryResponse.of(target.orderId(), Outcome.FAILED, code);
    }

    private PaymentRecoveryResponse unresolved(PurchaseRecoveryTarget target, String reason) {
        return PaymentRecoveryResponse.of(target.orderId(), Outcome.UNRESOLVED, reason);
    }
}
