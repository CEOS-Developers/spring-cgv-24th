package com.ceos24.cgv.domain.movie.dto.request;

import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;

import java.time.LocalDate;

public record MovieUpdateRequest(
        String title,
        String genre,
        Integer runningTime,
        String description,
        AgeRating ageRating,
        MovieStatus status,
        LocalDate openDate,
        LocalDate closeDate
) {
}
