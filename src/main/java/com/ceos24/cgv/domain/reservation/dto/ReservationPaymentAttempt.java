package com.ceos24.cgv.domain.reservation.dto;

import java.time.LocalDateTime;

// 승인 시작(T1)이 확인을 마친 값을 토스 호출과 결과 반영(T2)으로 넘긴다. T2는 요청 본문을 다시 보지 않는다.
// 결제 전 만료 시각은 거절됐을 때 늘린 선점 시간을 되돌리는 데 쓴다.
public record ReservationPaymentAttempt(
        Long paymentId,
        Long reservationId,
        Long userId,
        String paymentKey,
        String orderId,
        int amount,
        LocalDateTime expiresAtBeforePayment
) {
}
