package com.ceos.cgv.domain.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Builder;

@Builder
public record PaymentOrderCreateRequest(@NotNull @Positive Long reservationId) {}
