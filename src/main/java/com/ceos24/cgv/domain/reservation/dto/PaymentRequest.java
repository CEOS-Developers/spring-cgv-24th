package com.ceos24.cgv.domain.reservation.dto;

import jakarta.validation.constraints.NotNull;

// 실제 결제는 mock이다. 결제수단·PG 연동 없이 완료/실패 전이만 다룬다.
public record PaymentRequest(@NotNull PaymentResult result) {

    public enum PaymentResult {
        SUCCESS, FAILURE
    }
}
