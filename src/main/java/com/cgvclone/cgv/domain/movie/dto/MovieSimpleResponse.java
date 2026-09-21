package com.cgvclone.cgv.domain.movie.dto;

import com.cgvclone.cgv.domain.movie.ContentRating;
import com.cgvclone.cgv.domain.movie.Movie;

public record MovieSimpleResponse(
        Long movieId,
        String title,
        ContentRating contentRatingCode,
        String posterUrl
) {
    public static MovieSimpleResponse from(Movie movie) {
        return new MovieSimpleResponse(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getContentRatingCode(),
                movie.getPosterUrl()
        );
    }
}
