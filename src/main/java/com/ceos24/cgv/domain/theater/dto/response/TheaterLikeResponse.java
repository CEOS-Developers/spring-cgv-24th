package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.entity.TheaterLike;

public record TheaterLikeResponse(
        Long likeId,
        Long theaterId,
        String theaterName
) {
    public static TheaterLikeResponse from(TheaterLike theaterLike) {
        return new TheaterLikeResponse(
                theaterLike.getId(),
                theaterLike.getTheater().getId(),
                theaterLike.getTheater().getName()
        );
    }
}
