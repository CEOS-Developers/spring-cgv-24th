package com.spring_cgv_24th.domain.favorite.dto;

import com.spring_cgv_24th.domain.favorite.entity.TheaterFavorite;

public record TheaterFavoriteResDTO(Long theaterFavoriteId, Long theaterId) {

    public static TheaterFavoriteResDTO from(TheaterFavorite favorite) {
        return new TheaterFavoriteResDTO(favorite.getId(), favorite.getTheater().getId());
    }
}
