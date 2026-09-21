package com.ceos.cgv.domain.movie.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ScreeningCreateRequest(
        @NotNull Long movieId,
        @NotNull Long screenId,
        @NotNull LocalDateTime startAt
) {
}
