package com.ceos24.cgv.domain.reservation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
    HOLD("결제 대기 중"),
    CONFIRMED("예매 확정"),
    CANCELED("예매 취소"),
    EXPIRED("점유 만료");

    private final String description;
}
