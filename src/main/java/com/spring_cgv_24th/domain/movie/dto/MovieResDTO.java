package com.spring_cgv_24th.domain.movie.dto;

import com.spring_cgv_24th.domain.movie.entity.Movie;
import java.time.LocalDate;

public record MovieResDTO(
        Long movieId,
        String title,
        String description,
        short durationMinutes,
        String ageRating,
        LocalDate releaseDate,
        String posterUrl
) {

    public static MovieResDTO from(Movie movie) {
        return new MovieResDTO(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getDurationMinutes(),
                movie.getAgeRating(),
                movie.getReleaseDate(),
                movie.getPosterUrl());
    }
}
