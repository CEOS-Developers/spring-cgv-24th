package com.ceos24.cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        String method,
        String currency,
        Long totalAmount,
        Long balanceAmount,
        OffsetDateTime approvedAt,
        String lastTransactionKey,
        List<Cancel> cancels
) {
    public TossPaymentResponse {
        cancels = cancels == null ? List.of() : List.copyOf(cancels);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancel(String transactionKey, Long cancelAmount, OffsetDateTime canceledAt,
                         Long refundableAmount, String cancelStatus) {
    }
}
