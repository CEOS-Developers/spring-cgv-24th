package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.entity.UserTheater;
import com.ceos24.cgv.domain.theater.enums.TheaterRegion;

public record UserTheaterResponse(
        Long userTheaterId,
        Long userId,
        Long theaterId,
        String theaterName,
        String address,
        TheaterRegion region
) {

    public static UserTheaterResponse from(
            UserTheater userTheater
    ) {
        return new UserTheaterResponse(
                userTheater.getId(),
                userTheater.getUser().getId(),
                userTheater.getTheater().getId(),
                userTheater.getTheater().getName(),
                userTheater.getTheater().getAddress(),
                userTheater.getTheater().getRegion()
        );
    }
}