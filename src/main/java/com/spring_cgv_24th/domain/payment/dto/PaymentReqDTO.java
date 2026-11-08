package com.spring_cgv_24th.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentReqDTO(
        @NotBlank @Size(max = 200) String paymentKey,
        @NotBlank @Size(min = 6, max = 64) String paymentOrderId,
        @NotNull @Positive Long amount
) {
}
