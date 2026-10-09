package com.ceos24.cgv.global.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

// 토스 Payment 객체 중 우리가 저장하거나 대조하는 필드만 받는다. 토스가 필드를 추가해도 역직렬화가 깨지지 않게 나머지는 무시한다.
// 승인·조회·취소가 모두 이 객체를 돌려준다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(
        String paymentKey,
        String orderId,
        String status,
        String method,
        int totalAmount,
        int balanceAmount,
        OffsetDateTime approvedAt,
        List<Cancel> cancels
) {
    private static final String DONE = "DONE";

    // 취소가 한 번도 없으면 토스는 cancels를 null로 준다. 호출하는 쪽이 null을 신경 쓰지 않게 빈 목록으로 맞춘다.
    public TossPayment {
        cancels = cancels == null ? List.of() : List.copyOf(cancels);
    }

    public boolean isDone() {
        return DONE.equals(status);
    }

    // 토스는 +09:00 오프셋을 붙여 준다. 우리 LocalDateTime은 서버 시계의 시간대 기준이라 그 시간대로 맞춘다.
    public LocalDateTime approvedAtIn(ZoneId zone, LocalDateTime fallback) {
        return approvedAt == null ? fallback : approvedAt.atZoneSameInstant(zone).toLocalDateTime();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancel(
            String transactionKey,
            int cancelAmount,
            String cancelReason,
            String cancelStatus,
            OffsetDateTime canceledAt
    ) {
        public LocalDateTime canceledAtIn(ZoneId zone, LocalDateTime fallback) {
            return canceledAt == null ? fallback : canceledAt.atZoneSameInstant(zone).toLocalDateTime();
        }
    }
}
