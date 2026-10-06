package com.ceos24.cgv.domain.movie.dto;

import com.ceos24.cgv.domain.movie.entity.Movie;

import java.time.LocalDate;

public record MovieResponse(
        Long id,
        String title,
        String director,
        String genre,
        int runningTime,
        LocalDate releaseDate,
        String ageRating,
        long reservedSeatCount
) {

    public static MovieResponse from(Movie movie, long reservedSeatCount) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDirector(),
                movie.getGenre(),
                movie.getRunningTime(),
                movie.getReleaseDate(),
                movie.getAgeRating(),
                reservedSeatCount
        );
    }
}
