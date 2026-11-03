package com.ceos24.springboot.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentCancelResponse(
        String paymentKey, String orderId, Long totalAmount, String currency,
        String status, Long balanceAmount, List<Cancel> cancels
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancel(String transactionKey, Long cancelAmount, String cancelReason,
                         String cancelStatus, OffsetDateTime canceledAt) {}
}
