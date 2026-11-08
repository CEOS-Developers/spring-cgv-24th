package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.store.dto.PurchaseRefundAttempt;
import com.ceos24.cgv.domain.store.dto.PurchaseRefundRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.global.client.TossCancelResult.Canceled;
import com.ceos24.cgv.global.client.TossCancelResult.Rejected;
import com.ceos24.cgv.global.client.TossCancelResult.Unknown;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 환불 T1 → 토스 취소 → T2를 차례로 잇기만 한다. @Transactional을 붙이지 않는 이유는 PurchasePaymentService와 같다.
// 여기서는 붙이면 구매 행 락을 토스 응답을 기다리는 동안 쥐어, 같은 구매의 다른 환불 요청이 락 대기에 걸린다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRefundService {

    private final PurchaseService purchaseService;
    private final TossPaymentsClient tossPaymentsClient;

    public PurchaseResponse refundAll(Long id) {
        return refund(purchaseService.startRefund(id, null));
    }

    public PurchaseResponse refundItems(Long id, PurchaseRefundRequest req) {
        return refund(purchaseService.startRefund(id, req.items()));
    }

    public PurchaseResponse retry(Long id, Long paymentCancelId) {
        return refund(purchaseService.startRefundRetry(id, paymentCancelId));
    }

    private PurchaseResponse refund(PurchaseRefundAttempt attempt) {
        return switch (tossPaymentsClient.cancel(attempt.paymentKey(), attempt.idempotencyKey(),
                attempt.amount(), attempt.reason())) {
            case Canceled canceled -> complete(attempt, canceled);
            case Rejected rejected -> {
                fail(attempt, rejected);
                throw new CustomException(ErrorCode.PAYMENT_CANCEL_REJECTED);
            }
            // 수량을 반영하지 않는다. 실제로는 환불되지 않았는데 반영하면 남은 수량만큼 다시 환불할 수 없게 된다.
            case Unknown unknown -> throw new CustomException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
        };
    }

    // 토스는 이미 환불했다. 500으로 내보내면 환불이 안 된 줄 알게 된다. 재시도하면 첫 응답이 돌아와 그때 반영된다.
    private PurchaseResponse complete(PurchaseRefundAttempt attempt, Canceled canceled) {
        try {
            return purchaseService.completeRefund(attempt, canceled.cancel(), canceled.payment().balanceAmount());
        } catch (RuntimeException e) {
            log.error("[Payment] 토스 환불 후 반영 실패 paymentCancelId={} transactionKey={}",
                    attempt.paymentCancelId(), canceled.cancel().transactionKey(), e);
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
        }
    }

    // 반영에 실패해도 맞는 답은 "환불 거절"이다. 취소가 IN_PROGRESS로 남지만 재시도하면 같은 거절이 와서 정리된다.
    private void fail(PurchaseRefundAttempt attempt, Rejected rejected) {
        try {
            purchaseService.failRefund(attempt, rejected.code(), rejected.message());
        } catch (RuntimeException e) {
            log.error("[Payment] 토스 환불 거절 후 반영 실패 paymentCancelId={}", attempt.paymentCancelId(), e);
        }
    }
}
