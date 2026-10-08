package com.ceos24.cgv.domain.store.dto;

// 승인 시작(T1)이 확인을 마친 값을 토스 호출과 결과 반영(T2)으로 넘긴다. T2는 요청 본문을 다시 보지 않는다.
public record PurchasePaymentAttempt(
        Long paymentId,
        Long purchaseId,
        String paymentKey,
        String orderId,
        int amount
) {
}
