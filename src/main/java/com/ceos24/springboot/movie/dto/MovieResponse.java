package com.ceos24.springboot.movie.dto;

import com.ceos24.springboot.movie.domain.AgeRating;
import com.ceos24.springboot.movie.domain.Movie;

import java.time.LocalDate;

public record MovieResponse(
        Long movieId,
        String titleKr,
        String titleEn,
        Integer runtimeMinutes,
        AgeRating ageRating,
        LocalDate releaseDate,
        String director,
        String castMembers
) {
//    정적 팩토리 메서드 from 사용
    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getMovieId(),
                movie.getTitleKr(),
                movie.getTitleEn(),
                movie.getRuntimeMinutes(),
                movie.getAgeRating(),
                movie.getReleaseDate(),
                movie.getDirector(),
                movie.getCastMembers()
        );
    }
}