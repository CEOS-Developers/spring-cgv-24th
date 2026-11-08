package com.cgvclone.cgv.domain.payment;

import java.time.OffsetDateTime;

public record TossPayment(
        String orderId,
        String paymentKey,
        Integer totalAmount,
        String currency,
        String status,
        String method,
        OffsetDateTime approvedAt
) {
}
