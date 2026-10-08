package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.store.dto.PurchasePaymentAttempt;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 승인 T1 → 토스 호출 → T2를 차례로 잇기만 한다. 트랜잭션은 PurchaseService의 메서드가 각각 연다.
// @Transactional을 붙이지 않는 이유는 ReservationPaymentService와 같다. 여기서는 붙이면 재고 행 락을
// 토스 응답을 기다리는 동안 쥐어, 같은 상품을 사려는 모든 주문이 줄을 선다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePaymentService {

    private final PurchaseService purchaseService;
    private final TossPaymentsClient tossPaymentsClient;

    public PurchaseResponse confirm(Long id, Long userId, PaymentConfirmRequest req) {
        PurchasePaymentAttempt attempt = purchaseService.startPayment(id, userId, req);

        return switch (tossPaymentsClient.confirm(attempt.paymentKey(), attempt.orderId(), attempt.amount())) {
            case Approved approved -> complete(attempt, approved);
            case Rejected rejected -> {
                fail(attempt, rejected);
                throw new CustomException(ErrorCode.PAYMENT_REJECTED);
            }
            // 재고는 차감된 채로 둔다. 실패로 보고 되돌렸는데 토스에서는 승인됐다면 판 수량보다 재고가 많아진다.
            case Unknown unknown -> throw new CustomException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        };
    }

    // 토스는 이미 승인했다. 여기서 나는 예외를 그대로 내보내면 500이 되어 회원은 결제가 안 된 줄 안다.
    private PurchaseResponse complete(PurchasePaymentAttempt attempt, Approved approved) {
        try {
            return purchaseService.completePayment(attempt, approved.payment());
        } catch (RuntimeException e) {
            log.error("[Payment] 토스 승인 후 반영 실패 orderId={} paymentKey={}",
                    attempt.orderId(), approved.payment().paymentKey(), e);
            throw new CustomException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        }
    }

    // 재고 복구는 락 대기에 걸릴 수 있다. 그래도 회원에게 맞는 답은 "결제 거절"이다. 돈은 빠지지 않았다.
    // 복구가 롤백되면 결제가 IN_PROGRESS로 남아 결제 조회로 다시 정리할 단서가 된다.
    private void fail(PurchasePaymentAttempt attempt, Rejected rejected) {
        try {
            purchaseService.failPayment(attempt, rejected.code(), rejected.message());
        } catch (RuntimeException e) {
            log.error("[Payment] 토스 거절 후 재고 복구 실패 orderId={}", attempt.orderId(), e);
        }
    }
}
