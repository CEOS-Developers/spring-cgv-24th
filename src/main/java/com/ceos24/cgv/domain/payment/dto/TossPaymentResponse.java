package com.ceos24.cgv.domain.payment.dto;

import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
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

    public void validateIntegrity(String expectedOrderId, Long expectedAmount) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_RESPONSE, "토스 승인 응답에 paymentKey가 누락되었습니다.");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_RESPONSE, "토스 승인 응답에 orderId가 누락되었습니다.");
        }
        if (expectedOrderId != null && !orderId.equals(expectedOrderId)) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_RESPONSE, "토스 승인 응답의 orderId가 요청과 일치하지 않습니다.");
        }
        if (totalAmount == null || totalAmount <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_RESPONSE, "토스 승인 응답의 결제 금액(totalAmount)이 올바르지 않습니다.");
        }
        if (expectedAmount != null && !totalAmount.equals(expectedAmount)) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_RESPONSE, "토스 승인 응답의 결제 금액이 요청 금액과 일치하지 않습니다.");
        }
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
