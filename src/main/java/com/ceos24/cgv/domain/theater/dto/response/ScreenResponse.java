package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.enums.TheaterType;

public record ScreenResponse(
        Long screenId,
        TheaterType theaterType,
        String screenName,
        Integer seatCount,
        Long screenTypeId,
        String screenTypeName,
        Integer basePrice
) {

    public static ScreenResponse from(Screen screen) {
        return new ScreenResponse(
                screen.getId(),
                screen.getTheaterType(),
                screen.getName(),
                screen.getSeatCount(),
                screen.getScreenType().getId(),
                screen.getScreenType().getName(),
                screen.getScreenType().getBasePrice()
        );
    }
}