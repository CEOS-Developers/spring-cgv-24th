package com.ceos24.cgv.domain.reservation.enums;

public enum ReservationStatus {
    PENDING_PAYMENT,     // 좌석 확보 후 결제 대기
    CONFIRMED,           // 결제 완료
    PARTIALLY_CANCELED,  // 일부 좌석 취소
    CANCELED,            // 전체 취소
    EXPIRED              // 미결제 상태로 대기 시간 만료
}