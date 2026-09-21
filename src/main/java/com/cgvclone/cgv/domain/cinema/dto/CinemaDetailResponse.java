package com.cgvclone.cgv.domain.cinema.dto;

import com.cgvclone.cgv.domain.cinema.Cinema;

public record CinemaDetailResponse(
        Long cinemaId,
        String name,
        String address
) {
    public static CinemaDetailResponse from(Cinema cinema) {
        return new CinemaDetailResponse(
                cinema.getCinemaId(),
                cinema.getName(),
                cinema.getAddress()
        );
    }
}
