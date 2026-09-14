package com.ceos.cgv.domain.cinema.dto;

import com.ceos.cgv.domain.cinema.entity.Cinema;

public record CinemaResponse(
        Long cinemaId,
        String name,
        String address
) {
    public static CinemaResponse from(Cinema cinema){
        return new CinemaResponse(
                cinema.getId(),
                cinema.getName(),
                cinema.getAddress()
        );
    }
}
