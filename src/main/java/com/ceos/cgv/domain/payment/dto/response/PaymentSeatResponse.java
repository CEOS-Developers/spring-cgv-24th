package com.ceos.cgv.domain.payment.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PaymentSeatResponse(
        Long reservedSeatId, String seatRow, Integer seatNumber, Instant canceledAt) {}
