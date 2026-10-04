package com.ceos.cgv.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Builder;

import java.util.List;

@Builder
public record PaymentCancelRequest(
        List<@NotNull @Positive Long> reservedSeatIds, @NotBlank @Size(max = 200) String reason) {}
