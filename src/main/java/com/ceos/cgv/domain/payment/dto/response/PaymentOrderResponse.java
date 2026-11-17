package com.ceos.cgv.domain.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record PaymentOrderResponse(
        String orderId,
        Long paymentId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long reservationId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long foodOrderId,
        String orderName,
        long totalAmount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long seatPrice,
        Instant expiresAt,
        List<PaymentSeatResponse> seats) {
    public PaymentOrderResponse {
        seats = List.copyOf(seats);
    }
}
