package com.ceos24.cgv.domain.payment.entity;

// IN_PROGRESS는 결제의 IN_PROGRESS와 같이 "토스를 부르는 중"이자 "토스 결과를 모름"이다.
// 둘 다 좌석·재고를 놓으면 안 된다는 점이 같다.
public enum PaymentCancelStatus {

    IN_PROGRESS("취소중"),
    DONE("취소완료"),
    FAILED("취소실패");

    private final String displayName;

    PaymentCancelStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
