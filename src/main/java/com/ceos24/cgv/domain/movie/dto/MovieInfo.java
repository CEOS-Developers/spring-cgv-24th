package com.ceos24.cgv.domain.movie.dto;

import com.ceos24.cgv.domain.movie.domain.Movie;

public record MovieInfo(
        Long movieId,
        String title
) {
    public static MovieInfo from(Movie movie) {
        return new MovieInfo(movie.getId(), movie.getTitle());
    }
}
