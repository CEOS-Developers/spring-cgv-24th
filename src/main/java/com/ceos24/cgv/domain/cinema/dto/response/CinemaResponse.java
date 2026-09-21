package com.ceos24.cgv.domain.cinema.dto.response;

import com.ceos24.cgv.domain.cinema.entity.Cinema;

public record CinemaResponse(
        Long id,
        String name,
        String address,
        String region
) {

    public static CinemaResponse from(Cinema cinema) {
        return new CinemaResponse(
                cinema.getId(),
                cinema.getName(),
                cinema.getAddress(),
                cinema.getRegion()
        );
    }
}
