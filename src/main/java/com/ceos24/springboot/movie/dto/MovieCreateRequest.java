package com.ceos24.springboot.movie.dto;

import com.ceos24.springboot.movie.domain.AgeRating;
import com.ceos24.springboot.movie.domain.Movie;

import java.time.LocalDate;

public record MovieCreateRequest(
        String titleKr,
        String titleEn,
        Integer runtimeMinutes,
        AgeRating ageRating,
        LocalDate releaseDate,
        String director,
        String castMembers
) {

    public Movie toEntity() {
        return Movie.builder()
                .titleKr(titleKr)
                .titleEn(titleEn)
                .runtimeMinutes(runtimeMinutes)
                .ageRating(ageRating)
                .releaseDate(releaseDate)
                .director(director)
                .castMembers(castMembers)
                .build();
    }
}