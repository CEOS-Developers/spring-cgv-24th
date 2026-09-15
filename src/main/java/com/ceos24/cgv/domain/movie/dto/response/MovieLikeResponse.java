package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.MovieLike;

public record MovieLikeResponse(
        Long likeId,
        Long movieId,
        String movieTitle
) {
    public static MovieLikeResponse from(MovieLike movieLike) {
        return new MovieLikeResponse(
                movieLike.getId(),
                movieLike.getMovie().getId(),
                movieLike.getMovie().getTitle()
        );
    }
}
