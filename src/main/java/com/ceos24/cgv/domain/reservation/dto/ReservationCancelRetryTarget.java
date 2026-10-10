package com.ceos24.cgv.domain.reservation.dto;

// 결과 불명 취소를 자동으로 다시 보낼 때 회원 재시도 경로(ReservationCancelService.retry)에 넘길 값이다.
public record ReservationCancelRetryTarget(
        Long reservationId,
        Long userId,
        Long paymentCancelId
) {
}
