package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.dto.ScreeningMovieInfo;

import java.util.List;

public record GetScreeningResponse (
        Long theaterId,
        List<ScreeningMovieInfo> movies
){
}
