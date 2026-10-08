package com.ceos24.cgv.domain.store.entity;

// 구매는 결제가 끝나야 성립한다. 결제창을 닫고 떠난 주문(PENDING)과 거절된 주문(FAILED)은 내역에 보이지 않는다.
public enum PurchaseStatus {

    PENDING("결제대기"),
    PAID("결제완료"),
    FAILED("결제실패");

    private final String displayName;

    PurchaseStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
