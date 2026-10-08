package com.ceos24.cgv.domain.payment.entity;

// 우리 쪽 결제 시도의 상태다. 토스의 Payment status와 1:1로 대응하지 않는다.
// IN_PROGRESS는 "토스를 부르는 중"이자 "토스 결과를 모름"이다. 둘 다 확정하면 안 된다는 점이 같아 나누지 않는다.
public enum PaymentStatus {

    READY("결제대기"),
    IN_PROGRESS("승인중"),
    DONE("결제완료"),
    FAILED("결제실패");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
