package com.ceos24.cgv.domain.store.entity;

public enum PurchaseStatus {
    PENDING_PAYMENT,
    PAID,
    PARTIALLY_CANCELED,
    CANCELED,
    EXPIRED,
    // 결제 연동 전에 생성된 구매 이력이며, 토스 승인 기록이 있다는 의미는 아니다.
    LEGACY_COMPLETED
}
