package com.ceos24.cgv.domain.cinema.dto.response;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.entity.CinemaFavorite;
import com.ceos24.cgv.domain.user.entity.User;

import java.time.LocalDateTime;

public record CinemaFavoriteResponse(
        Long id,
        Long userId,
        String userName,
        Long cinemaId,
        String cinemaName,
        String address,
        String region,
        LocalDateTime createdAt
) {
    public static CinemaFavoriteResponse from(CinemaFavorite cinemaFavorite) {
        User user = cinemaFavorite.getUser();
        Cinema cinema = cinemaFavorite.getCinema();

        return new CinemaFavoriteResponse(
                cinemaFavorite.getId(),
                user.getId(),
                user.getName(),
                cinema.getId(),
                cinema.getName(),
                cinema.getAddress(),
                cinema.getRegion(),
                cinemaFavorite.getCreatedAt()
        );
    }
}
