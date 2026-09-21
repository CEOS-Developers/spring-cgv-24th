package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieFavorite;
import com.ceos24.cgv.domain.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MovieFavoriteResponse(
        Long id,
        Long userId,
        String userName,
        Long movieId,
        String movieName,
        LocalDate releaseDate,
        LocalDateTime createdAt
) {
    public static MovieFavoriteResponse from(
            MovieFavorite movieFavorite
    ) {
        Movie movie = movieFavorite.getMovie();
        User user = movieFavorite.getUser();

        return new MovieFavoriteResponse(
                movieFavorite.getId(),
                user.getId(),
                user.getName(),
                movie.getId(),
                movie.getName(),
                movie.getReleaseDate(),
                movieFavorite.getCreatedAt()
        );
    }
}
