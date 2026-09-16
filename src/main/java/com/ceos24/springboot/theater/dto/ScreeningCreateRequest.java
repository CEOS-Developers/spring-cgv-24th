package com.ceos24.springboot.theater.dto;

import com.ceos24.springboot.theater.domain.ScreeningType;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScreeningCreateRequest(
        Long screenId,
        Long movieId,
        LocalDate screeningDate,
        LocalTime startTime,
        LocalTime endTime,
        ScreeningType screeningType
) {
}