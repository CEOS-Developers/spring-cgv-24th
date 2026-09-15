package com.ceos.cgv.domain.movie.dto;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.enums.AgeRating;

import java.time.LocalDate;

public record MovieResponse(
        Long movieId,
        String title,
        String description,
        Integer runningTime,
        LocalDate releaseDate,
        AgeRating ageRating
) {
    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getRunningTime(),
                movie.getReleaseDate(),
                movie.getAgeRating()
        );
    }
}
