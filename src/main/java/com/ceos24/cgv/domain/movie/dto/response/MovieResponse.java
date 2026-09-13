package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.Movie;

import java.time.LocalDate;

public record MovieResponse(
        Long id,
        String name,
        LocalDate releaseDate
) {

    // 정적 팩토리 메서드
    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getName(),
                movie.getReleaseDate()
        );
    }
}
