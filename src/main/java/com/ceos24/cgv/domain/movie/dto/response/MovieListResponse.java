package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;

public record MovieListResponse(
        Long id,
        String title,
        String moviePosterUrl,
        AgeRating ageRating,
        MovieStatus status,
        Integer audienceCount,
        Double reservationRate,
        Double eggScore
) {
    public static MovieListResponse of(Movie movie, String moviePosterUrl,
                                       Integer audienceCount, Double reservationRate, Double eggScore) {
        return new MovieListResponse(
                movie.getId(), movie.getTitle(), moviePosterUrl, movie.getAgeRating(),
                movie.getStatus(),
                audienceCount, reservationRate, eggScore
        );
    }
}
