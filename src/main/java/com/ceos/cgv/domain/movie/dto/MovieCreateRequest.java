package com.ceos.cgv.domain.movie.dto;

import com.ceos.cgv.domain.movie.enums.AgeRating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record MovieCreateRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull @Positive Integer runningTime,
        @NotNull LocalDate releaseDate,
        @NotNull AgeRating ageRating
) {
}
