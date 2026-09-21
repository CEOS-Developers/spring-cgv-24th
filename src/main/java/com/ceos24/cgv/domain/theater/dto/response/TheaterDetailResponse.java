package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.enums.TheaterRegion;

import java.util.List;

public record TheaterDetailResponse(
        Long theaterId,
        String name,
        String address,
        TheaterRegion region,
        List<ScreenResponse> screens
) {

    public static TheaterDetailResponse from(
            Theater theater,
            List<Screen> screens
    ) {
        List<ScreenResponse> screenResponses = screens.stream()
                .map(ScreenResponse::from)
                .toList();

        return new TheaterDetailResponse(
                theater.getId(),
                theater.getName(),
                theater.getAddress(),
                theater.getRegion(),
                screenResponses
        );
    }
}