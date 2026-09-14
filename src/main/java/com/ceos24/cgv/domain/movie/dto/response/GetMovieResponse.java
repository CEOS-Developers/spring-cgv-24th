package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.dto.MovieInfo;

import java.util.List;

public record GetMovieResponse (
    List<MovieInfo> movies
){
}
