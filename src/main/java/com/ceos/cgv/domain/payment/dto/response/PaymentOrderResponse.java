package com.ceos.cgv.domain.payment.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record PaymentOrderResponse(
        String orderId,
        Long paymentId,
        Long reservationId,
        String orderName,
        long totalAmount,
        long seatPrice,
        Instant expiresAt,
        List<PaymentSeatResponse> seats) {
    public PaymentOrderResponse {
        seats = List.copyOf(seats);
    }
}
