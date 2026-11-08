package com.ceos24.cgv.domain.store.dto;

// 결과 불명 환불을 자동으로 다시 보낼 때 관리자 재시도 경로(PurchaseRefundService.retry)에 넘길 값이다.
public record PurchaseRefundRetryTarget(
        Long purchaseId,
        Long paymentCancelId
) {
}
