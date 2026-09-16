package com.cgvclone.cgv.domain.movie.dto;

import com.cgvclone.cgv.domain.movie.Movie;
import java.util.List;

public record MovieListResponse(
        List<MovieSimpleResponse> movies
) {
    public static MovieListResponse from(List<Movie> movies) {
        List<MovieSimpleResponse> movieSimpleResponses = movies.stream()
                .map(MovieSimpleResponse::from)
                .toList();
        return new MovieListResponse(movieSimpleResponses);
    }
}
