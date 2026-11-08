package com.ceos24.cgv.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentConfirmRequest(@NotBlank String paymentKey, @NotBlank String orderId, @NotNull @Positive Integer amount) {
}
