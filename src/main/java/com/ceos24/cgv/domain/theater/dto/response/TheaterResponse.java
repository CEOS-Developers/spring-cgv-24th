package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.entity.Theater;

public record TheaterResponse(
        Long id,
        String name,
        String region,
        String address,
        String description,
        String theaterImageUrl
) {
    public static TheaterResponse from(Theater theater) {
        return new TheaterResponse(
                theater.getId(),
                theater.getName(),
                theater.getRegion(),
                theater.getAddress(),
                theater.getDescription(),
                theater.getTheaterImageUrl()
        );
    }
}
