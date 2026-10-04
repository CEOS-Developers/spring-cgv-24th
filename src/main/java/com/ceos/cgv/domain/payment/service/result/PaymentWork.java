package com.ceos.cgv.domain.payment.service.result;

import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationType;

import lombok.Builder;

@Builder
public record PaymentWork(
        Long operationId,
        PaymentOperationType type,
        String paymentKey,
        String orderId,
        long amount,
        long previousBalance,
        String reason,
        String gatewayKey,
        boolean compensating,
        boolean callGateway,
        PaymentOperationResponse response) {}
