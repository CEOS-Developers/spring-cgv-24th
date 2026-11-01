package com.ceos24.cgv.domain.payment.dto;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        String paymentKey,
        String orderId,
        Long totalAmount,
        Long balanceAmount,
        PaymentStatus status,
        String method,
        LocalDateTime approvedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentKey(),
                payment.getOrderId(),
                payment.getTotalAmount(),
                payment.getBalanceAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getApprovedAt());
    }
}
