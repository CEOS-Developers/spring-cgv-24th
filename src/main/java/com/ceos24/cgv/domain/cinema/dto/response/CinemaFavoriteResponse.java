package com.ceos24.cgv.domain.cinema.dto.response;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.entity.CinemaFavorite;
import com.ceos24.cgv.domain.user.entity.UserEntity;

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
        UserEntity userEntity = cinemaFavorite.getUserEntity();
        Cinema cinema = cinemaFavorite.getCinema();

        return new CinemaFavoriteResponse(
                cinemaFavorite.getId(),
                userEntity.getId(),
                userEntity.getUsername(),
                cinema.getId(),
                cinema.getName(),
                cinema.getAddress(),
                cinema.getRegion(),
                cinemaFavorite.getCreatedAt()
        );
    }
}
