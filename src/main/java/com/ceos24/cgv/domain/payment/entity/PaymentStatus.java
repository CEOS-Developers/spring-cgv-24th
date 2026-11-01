package com.ceos24.cgv.domain.payment.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    READY("결제 준비"),
    DONE("결제 완료"),
    PARTIAL_CANCELED("부분 취소"),
    CANCELED("전액 취소"),
    ABORTED("결제 승인 실패");

    private final String description;
}
