package com.ceos24.cgv.domain.movie.dto.request;

import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record MovieUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "제목은 공백일 수 없습니다.")
        String title,

        String genre,

        @Positive(message = "러닝타임은 양수여야 합니다.")
        Integer runningTime,

        String description,

        AgeRating ageRating,

        MovieStatus status,

        LocalDate openDate,

        LocalDate closeDate
) {
}
