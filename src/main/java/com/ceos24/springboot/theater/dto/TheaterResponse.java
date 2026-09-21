package com.ceos24.springboot.theater.dto;

import com.ceos24.springboot.theater.domain.Region;
import com.ceos24.springboot.theater.domain.Theater;

public record TheaterResponse(
        Long theaterId,
        String theaterName,
        Region region
) {

    public static TheaterResponse from(Theater theater) {
        return new TheaterResponse(
                theater.getTheaterId(),
                theater.getTheaterName(),
                theater.getRegion()
        );
    }
}