package com.ceos24.cgv.domain.screening.dto;

import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import com.ceos24.cgv.domain.theater.dto.ScreenInfo;

import java.time.LocalDateTime;

public record ScreeningInfo (
        MovieInfo movieInfo,
        ScreenInfo screenInfo,
        LocalDateTime startTime,
        LocalDateTime endTime
){
}
