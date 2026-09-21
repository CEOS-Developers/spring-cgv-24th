package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieStatistic;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;

import java.time.LocalDate;

public record MovieDetailResponse(
        Long movieId,
        String title,
        Integer runningTime,
        String description,
        LocalDate openDate,
        LocalDate endDate,
        MovieStatus status,
        MovieStatisticResponse statistic
) {
    public static MovieDetailResponse from(Movie movie) {
        MovieStatistic statistic = movie.getMovieStatistic();
        return new MovieDetailResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getRunningTime(),
                movie.getDescription(),
                movie.getOpenDate(),
                movie.getEndDate(),
                movie.getStatus(),
                // 통계가 없는 기존 데이터도 조회 가능하도록 !
                statistic == null
                        ? null
                        : MovieStatisticResponse.from(statistic)
                 );
    }
}