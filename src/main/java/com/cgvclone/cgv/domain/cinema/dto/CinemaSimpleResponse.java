package com.cgvclone.cgv.domain.cinema.dto;

import com.cgvclone.cgv.domain.cinema.Cinema;

public record CinemaSimpleResponse(
        Long cinemaId,
        String name,
        String address
) {
    public static CinemaSimpleResponse from(Cinema cinema) {
        return new CinemaSimpleResponse(
                cinema.getCinemaId(),
                cinema.getName(),
                cinema.getAddress()
        );
    }
}
