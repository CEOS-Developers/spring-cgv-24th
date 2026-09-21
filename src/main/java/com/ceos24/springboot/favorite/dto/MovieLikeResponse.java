package com.ceos24.springboot.favorite.dto;

import com.ceos24.springboot.favorite.domain.MovieLikes;

public record MovieLikeResponse(
        Long likeId,
        Long movieId,
        String titleKr,
        String titleEn
) {

    public static MovieLikeResponse from(MovieLikes movieLikes) {
        return new MovieLikeResponse(
                movieLikes.getLikeId(),
                movieLikes.getMovie().getMovieId(),
                movieLikes.getMovie().getTitleKr(),
                movieLikes.getMovie().getTitleEn()
        );
    }
}