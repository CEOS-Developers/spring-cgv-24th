package com.ceos24.cgv.dto.response;

import com.ceos24.cgv.domain.Movie;

import java.time.LocalDate;

public record MovieResponse(
        Long id,
        String title,
        String director,
        String genre,
        int runningTime,
        LocalDate releaseDate,
        String ageRating
) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDirector(),
                movie.getGenre(),
                movie.getRunningTime(),
                movie.getReleaseDate(),
                movie.getAgeRating()
        );
    }
}
