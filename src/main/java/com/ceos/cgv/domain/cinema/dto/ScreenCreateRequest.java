package com.ceos.cgv.domain.cinema.dto;

import com.ceos.cgv.domain.cinema.enums.ScreenType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ScreenCreateRequest(
        @NotNull Long cinemaId,
        @NotNull ScreenType screenType,
        @NotNull @Positive Integer rowCount,
        @NotNull @Positive Integer seatsPerRow
) {
}
