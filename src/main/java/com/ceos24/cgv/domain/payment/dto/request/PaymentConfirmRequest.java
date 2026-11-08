package com.ceos24.cgv.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentConfirmRequest(
        @NotBlank @Size(max = 200) String paymentKey,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_=\\-]{6,64}") String orderId,
        @Positive long amount
) {
}
