package com.spring_cgv_24th.domain.favorite.dto;

import com.spring_cgv_24th.domain.favorite.entity.MovieFavorite;

public record MovieFavoriteResDTO(Long movieFavoriteId, Long movieId) {

    public static MovieFavoriteResDTO from(MovieFavorite favorite) {
        return new MovieFavoriteResDTO(favorite.getId(), favorite.getMovie().getId());
    }
}
