package com.ceos24.cgv.domain.movie.dto;

import java.util.List;

public record ScreeningMovieInfo (
        Long movieId,
        String movieTitle,
        List<ScreenScheduleInfo> screens
){
}
