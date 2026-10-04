package com.ceos.cgv.domain.payment.infrastructure.toss.dto;

import lombok.Builder;

@Builder
public record TossCancelRequest(String cancelReason, long cancelAmount, long refundableAmount) {
}
