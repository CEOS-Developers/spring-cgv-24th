package com.cgvclone.cgv.domain.movie.dto;

import com.cgvclone.cgv.domain.movie.ContentRating;
import com.cgvclone.cgv.domain.movie.Movie;
import java.time.LocalDate;
import java.util.List;

public record MovieDetailResponse(
        Long movieId,
        String title,
        ContentRating contentRatingCode,
        Integer runningTime,
        LocalDate releaseDate,
        String description,
        String posterUrl,
        List<String> genres
) {
    public static MovieDetailResponse from(Movie movie) {
        List<String> genres = movie.getMovieGenres().stream()
                .map(movieGenre -> movieGenre.getGenre().getName())
                .toList();
        return new MovieDetailResponse(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getContentRatingCode(),
                movie.getRunningTime(),
                movie.getReleaseDate(),
                movie.getDescription(),
                movie.getPosterUrl(),
                genres
        );
    }
}
