package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;

import java.time.LocalDate;
import java.util.List;

public record MovieDetailResponse(
        Long id,
        String title,
        String genre,
        Integer runningTime,
        String description,
        AgeRating ageRating,
        MovieStatus status,
        LocalDate openDate,
        LocalDate closeDate,
        String moviePosterUrl,
        List<String> movieImageUrls,
        Integer audienceCount,
        Double reservationRate,
        Double eggScore,
        List<PersonResponse> persons
) {
    public static MovieDetailResponse of(Movie movie, String moviePosterUrl, List<String> movieImageUrls,
                                         Integer audienceCount, Double reservationRate, Double eggScore,
                                         List<PersonResponse> persons) {
        return new MovieDetailResponse(
                movie.getId(), movie.getTitle(), movie.getGenre(), movie.getRunningTime(),
                movie.getDescription(), movie.getAgeRating(), movie.getStatus(), movie.getOpenDate(), movie.getCloseDate(),
                moviePosterUrl, movieImageUrls,
                audienceCount, reservationRate, eggScore, persons
        );
    }
}
