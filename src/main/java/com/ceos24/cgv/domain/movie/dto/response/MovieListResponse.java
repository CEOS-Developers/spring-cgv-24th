package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;

import java.time.LocalDate;

public record MovieListResponse (
    Long movieId,
    String title,
    Integer runningTime,
    LocalDate openDate,
    MovieStatus status
    )
{
    public static MovieListResponse from(Movie movie) {
        return new MovieListResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getRunningTime(),
                movie.getOpenDate(),
                movie.getStatus()


        );
    }
}
