package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.domain.PaymentTargetType;

import java.time.LocalDateTime;

public record PaymentDetailResponse(
        Long paymentId,
        String orderId,
        PaymentTargetType targetType,
        Long targetId,
        String orderName,
        Long amount,
        String currency,
        String paymentMethod,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime approvedAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(payment.getId(), payment.getOrderId(), payment.getTargetType(),
                payment.getTargetId(), payment.getOrderName(), payment.getAmount(), payment.getCurrency(),
                payment.getPaymentMethod(), payment.getStatus(), payment.getCreatedAt(), payment.getApprovedAt());
    }
}
