package com.ceos24.cgv.domain.movie.dto.request;

import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record MovieCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,

        String genre,

        @NotNull(message = "러닝타임은 필수입니다.")
        @Positive(message = "러닝타임은 양수여야 합니다.")
        Integer runningTime,

        String description,

        @NotNull(message = "관람등급은 필수입니다.")
        AgeRating ageRating,

        @NotNull(message = "상영상태는 필수입니다.")
        MovieStatus status,

        @NotNull(message = "개봉일은 필수입니다.")
        LocalDate openDate,

        LocalDate closeDate
) {
}
