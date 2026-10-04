package com.ceos.cgv.domain.payment.gateway;

import lombok.Builder;
import java.time.Instant;

@Builder
public record GatewayCancellation(String transactionKey, long amount, Instant canceledAt, String status) {
}
