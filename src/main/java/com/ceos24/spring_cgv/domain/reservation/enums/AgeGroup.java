package com.ceos24.spring_cgv.domain.reservation.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AgeGroup {

    ADULT(0),
    YOUTH(-3000),
    SENIOR(-6000);

    // 성인가 대비 조정액 (할인이면 음수)
    private final int adjustment;
}