package com.ceos24.cgv.domain.theater.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScreenType {
    NORMAL("NORMAL_SCREEN", 14000L),
    SPECIAL("SPECIAL_SCREEN", 20000L);

    private final String value;
    private final Long ticketPrice;
}
