package com.ceos.cgv.domain.payment.service.result;

import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;

import lombok.Builder;

@Builder
public record PaymentOrderCreationResult(boolean created, PaymentOrderResponse response) {}
