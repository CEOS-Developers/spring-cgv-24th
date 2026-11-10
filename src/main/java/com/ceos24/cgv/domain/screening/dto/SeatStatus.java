package com.ceos24.cgv.domain.screening.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatStatus {
    AVAILABLE("예매 가능"),
    HOLD("결제 대기 중"),
    RESERVED("예매 완료");

    private final String description;
}
