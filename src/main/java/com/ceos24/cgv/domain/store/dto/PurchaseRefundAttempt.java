package com.ceos24.cgv.domain.store.dto;

// 환불 시작(T1)이 저장한 취소 요청을 토스 호출과 결과 반영(T2)으로 넘긴다.
// 재시도도 같은 값을 다시 만든다. 멱등키·금액·사유가 첫 요청과 같아야 토스가 같은 요청으로 본다.
public record PurchaseRefundAttempt(
        Long paymentCancelId,
        Long purchaseId,
        String paymentKey,
        String idempotencyKey,
        int amount,
        String reason
) {
}
