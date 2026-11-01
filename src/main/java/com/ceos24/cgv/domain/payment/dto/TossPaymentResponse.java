package com.ceos24.cgv.domain.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        String method,
        Long totalAmount,
        Long balanceAmount,
        String approvedAt,
        String requestedAt,
        List<TossCancel> cancels,
        TossCard card) {

    public TossPaymentResponse(
            String paymentKey,
            String orderId,
            String status,
            String method,
            Long totalAmount,
            Long balanceAmount,
            String approvedAt) {
        this(paymentKey, orderId, status, method, totalAmount, balanceAmount, approvedAt, null, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossCancel(
            Long cancelAmount, String cancelReason, Long refundableAmount, String canceledAt, String transactionKey) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossCard(
            Long amount,
            String issuerCode,
            String acquirerCode,
            String number,
            Integer installmentPlanMonths,
            String approveNo) {}
}
