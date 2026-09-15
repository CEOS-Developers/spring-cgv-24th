package com.ceos.cgv.domain.movie.dto;

import com.ceos.cgv.domain.cinema.enums.ScreenType;
import com.ceos.cgv.domain.movie.entity.Screening;

import java.time.LocalDateTime;

public record ScreeningResponse(
        Long screeningId,
        Long movieId,
        Long screenId,
        ScreenType screenType,
        LocalDateTime startAt
) {
    public static ScreeningResponse from(Screening screening) {
        return new ScreeningResponse(
                screening.getId(),
                screening.getMovie().getId(),
                screening.getScreen().getId(),
                screening.getScreen().getScreenType(),
                screening.getStartAt()
        );
    }
}
