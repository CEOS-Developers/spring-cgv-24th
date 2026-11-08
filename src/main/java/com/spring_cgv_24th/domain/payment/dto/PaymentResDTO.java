package com.spring_cgv_24th.domain.payment.dto;

import com.spring_cgv_24th.domain.payment.entity.Payment;
import com.spring_cgv_24th.domain.payment.enums.PaymentStatus;
import com.spring_cgv_24th.domain.payment.enums.PaymentTargetType;
import java.time.Instant;

public record PaymentResDTO(
        PaymentTargetType targetType,
        Long targetId,
        String paymentOrderId,
        String paymentKey,
        PaymentStatus status,
        long amount,
        long balanceAmount,
        String method,
        Instant approvedAt,
        String cancelReason
) {
    public static PaymentResDTO from(Payment payment) {
        return new PaymentResDTO(payment.getTargetType(), payment.getTargetId(), payment.getPaymentOrderId(),
                payment.getPaymentKey(), payment.getStatus(), payment.getAmount(), payment.getBalanceAmount(),
                payment.getMethod(), payment.getApprovedAt(), payment.getCancelReason());
    }
}
