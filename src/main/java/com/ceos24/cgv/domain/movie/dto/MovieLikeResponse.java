package com.ceos24.cgv.domain.movie.dto;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieLike;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MovieLikeResponse(
        Long movieId,
        String title,
        String genre,
        LocalDate releaseDate,
        String ageRating,
        LocalDateTime likedAt
) {
    public static MovieLikeResponse from(MovieLike like) {
        Movie movie = like.getMovie();
        return new MovieLikeResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getGenre(),
                movie.getReleaseDate(),
                movie.getAgeRating(),
                like.getCreatedAt()
        );
    }
}
