package com.ceos.cgv.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder
public record PaymentConfirmRequest(
        @NotBlank @Size(max = 200) String paymentKey,
        @NotBlank @Size(max = 64) String orderId,
        @NotNull @Positive Long amount) {}
