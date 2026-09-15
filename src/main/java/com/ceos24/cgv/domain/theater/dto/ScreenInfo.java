package com.ceos24.cgv.domain.theater.dto;

import com.ceos24.cgv.domain.theater.domain.Screen;
import com.ceos24.cgv.domain.theater.domain.ScreenType;

public record ScreenInfo (
        Long screenId,
        ScreenType screenType,
        String name,
        Long totalSeats
) {
    public static ScreenInfo from(Screen screen) {
        return new ScreenInfo(
                screen.getId(),
                screen.getScreenType(),
                screen.getName(),
                screen.getTotalSeats()
        );
    }
}
