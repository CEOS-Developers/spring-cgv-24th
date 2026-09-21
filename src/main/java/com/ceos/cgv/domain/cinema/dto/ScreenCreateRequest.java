package com.ceos.cgv.domain.cinema.dto;

import com.ceos.cgv.domain.cinema.enums.ScreenType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ScreenCreateRequest(
        @NotNull Long cinemaId,
        @NotNull ScreenType screenType,
        @NotNull @Positive @Max(26) Integer rowCount,
        @NotNull @Positive Integer seatsPerRow
) {
}
