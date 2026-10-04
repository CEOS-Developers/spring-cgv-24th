package com.ceos.cgv.domain.payment.dto.response;

import com.ceos.cgv.domain.payment.enums.PaymentOperationStatus;
import com.ceos.cgv.domain.payment.enums.PaymentOperationType;

import lombok.Builder;

@Builder
public record PaymentOperationResponse(
        Long operationId,
        PaymentOperationType type,
        PaymentOperationStatus status,
        Long paymentId,
        PaymentResponse result,
        String failureCode,
        String operationUrl) {}
