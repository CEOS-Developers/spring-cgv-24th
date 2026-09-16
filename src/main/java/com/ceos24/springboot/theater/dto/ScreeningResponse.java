package com.ceos24.springboot.theater.dto;

import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.theater.domain.ScreeningType;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScreeningResponse(
        Long screeningId,
        Long screenId,
        Long movieId,
        LocalDate screeningDate,
        LocalTime startTime,
        LocalTime endTime,
        ScreeningType screeningType
) {

    public static ScreeningResponse from(Screening screening) {
        return new ScreeningResponse(
                screening.getScreeningId(),
                screening.getScreen().getScreenId(),
                screening.getMovie().getMovieId(),
                screening.getScreeningDate(),
                screening.getStartTime(),
                screening.getEndTime(),
                screening.getScreeningType()
        );
    }
}