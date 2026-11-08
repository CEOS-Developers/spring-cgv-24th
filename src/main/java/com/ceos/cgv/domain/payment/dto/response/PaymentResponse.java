package com.ceos.cgv.domain.payment.dto.response;

import com.ceos.cgv.domain.payment.enums.PaymentStatus;
import com.ceos.cgv.domain.payment.gateway.GatewayCancellation;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record PaymentResponse(
        Long paymentId,
        String orderId,
        Long reservationId,
        String paymentKey,
        PaymentStatus status,
        Long activeOperationId,
        long totalAmount,
        long balanceAmount,
        String method,
        Instant approvedAt,
        String receiptUrl,
        List<GatewayCancellation> cancellations,
        List<PaymentSeatResponse> seats) {
    public PaymentResponse {
        seats = List.copyOf(seats);
        cancellations = List.copyOf(cancellations);
    }
}
