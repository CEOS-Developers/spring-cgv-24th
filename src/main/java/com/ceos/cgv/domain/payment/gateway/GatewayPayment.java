package com.ceos.cgv.domain.payment.gateway;

import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Builder
public record GatewayPayment(String paymentKey, String orderId, long totalAmount, long balanceAmount,
                             GatewayPaymentStatus status, String method, Instant approvedAt,
                             String receiptUrl, List<GatewayCancellation> cancellations) {
    public GatewayPayment {
        cancellations = cancellations == null ? List.of() : List.copyOf(cancellations);
    }
}
