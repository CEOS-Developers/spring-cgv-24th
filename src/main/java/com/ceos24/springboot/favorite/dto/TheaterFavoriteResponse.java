package com.ceos24.springboot.favorite.dto;

import com.ceos24.springboot.favorite.domain.TheaterFavorites;
import com.ceos24.springboot.theater.domain.Region;

public record TheaterFavoriteResponse(
        Long favoriteId,
        Long theaterId,
        String theaterName,
        Region region
) {

    public static TheaterFavoriteResponse from(
            TheaterFavorites theaterFavorites
    ) {
        return new TheaterFavoriteResponse(
                theaterFavorites.getFavoriteId(),
                theaterFavorites.getTheater().getTheaterId(),
                theaterFavorites.getTheater().getTheaterName(),
                theaterFavorites.getTheater().getRegion()
        );
    }
}