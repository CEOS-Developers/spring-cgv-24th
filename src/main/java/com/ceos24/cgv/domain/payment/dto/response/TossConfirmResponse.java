package com.ceos24.cgv.domain.payment.dto.response;

import java.time.OffsetDateTime;

public record TossConfirmResponse (
        String paymentKey,
        String orderId,
        Integer totalAmount,
        String status,
        OffsetDateTime approvedAt
){}
