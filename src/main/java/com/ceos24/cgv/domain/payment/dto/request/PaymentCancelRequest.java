package com.ceos24.cgv.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(
        @NotBlank @Size(max = 200) String cancelReason,
        // 생략하면 남은 금액 전체를 취소한다.
        @Positive Long cancelAmount
) {
}
