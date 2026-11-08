package com.ceos24.cgv.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// successUrl 쿼리로 받은 값을 그대로 옮긴다. amount는 대조용일 뿐 결제 금액으로 쓰지 않는다.
// 길이 제한은 토스 규칙이다. 어긋나는 값은 토스를 부르기 전에 400으로 끝낸다.
public record PaymentConfirmRequest(
        @NotBlank @Size(max = 200) String paymentKey,
        @NotBlank @Size(min = 6, max = 64) String orderId,
        @NotNull @Positive Integer amount
) {
}
