package com.ceos24.cgv.global.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// 토스 Payment 객체 중 우리가 저장하는 필드만 받는다. 토스가 필드를 추가해도 역직렬화가 깨지지 않게 나머지는 무시한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(
        String paymentKey,
        String orderId,
        String status,
        String method,
        int totalAmount,
        OffsetDateTime approvedAt
) {
    private static final String DONE = "DONE";

    public boolean isDone() {
        return DONE.equals(status);
    }

    // 토스는 +09:00 오프셋을 붙여 준다. 우리 LocalDateTime은 서버 시계의 시간대 기준이라 그 시간대로 맞춘다.
    public LocalDateTime approvedAtIn(ZoneId zone, LocalDateTime fallback) {
        return approvedAt == null ? fallback : approvedAt.atZoneSameInstant(zone).toLocalDateTime();
    }
}
