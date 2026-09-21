package com.ceos.cgv.domain.cinema.dto;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.cinema.enums.ScreenType;

public record ScreenResponse(
        Long screenId,
        Long cinemaId,
        ScreenType screenType,
        Integer rowCount,
        Integer seatsPerRow
) {
    public static ScreenResponse from(Screen screen) {
        return new ScreenResponse(
                screen.getId(),
                screen.getCinema().getId(),
                screen.getScreenType(),
                screen.getRowCount(),
                screen.getSeatsPerRow()
        );
    }
}
