package com.ceos24.cgv.domain.reservation.dto;

// 결과 불명 결제를 토스에 묻기 전에 읽어 둔 값이다. 토스를 기다리는 동안 트랜잭션을 열어 두지 않으려고
// 엔티티 대신 넘긴다. 반영할 때는 예매 행을 잠그고 결제 상태를 다시 확인한다.
public record ReservationRecoveryTarget(
        Long paymentId,
        Long reservationId,
        Long userId,
        String orderId,
        String paymentKey,
        int amount
) {
}
