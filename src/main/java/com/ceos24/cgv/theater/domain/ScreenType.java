package com.ceos24.cgv.theater.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScreenType {
    NORMAL("NORMAL_SCREEN"),
    SPECIAL("SPECIAL_SCREEN");

    private final String value;
}
