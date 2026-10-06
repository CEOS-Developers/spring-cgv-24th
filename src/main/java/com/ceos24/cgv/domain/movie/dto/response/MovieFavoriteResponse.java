package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieFavorite;
import com.ceos24.cgv.domain.user.entity.UserEntity;

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
        UserEntity userEntity = movieFavorite.getUserEntity();

        return new MovieFavoriteResponse(
                movieFavorite.getId(),
                userEntity.getId(),
                userEntity.getUsername(),
                movie.getId(),
                movie.getName(),
                movie.getReleaseDate(),
                movieFavorite.getCreatedAt()
        );
    }
}
