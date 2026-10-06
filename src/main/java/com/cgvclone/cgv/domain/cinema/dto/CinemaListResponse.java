package com.cgvclone.cgv.domain.cinema.dto;

import com.cgvclone.cgv.domain.cinema.Cinema;
import java.util.List;

public record CinemaListResponse(
        List<CinemaSimpleResponse> cinemas
) {
    public static CinemaListResponse from(List<Cinema> cinemas) {
        List<CinemaSimpleResponse> cinemaSimpleResponses = cinemas.stream()
                .map(CinemaSimpleResponse::from)
                .toList();
        return new CinemaListResponse(cinemaSimpleResponses);
    }
}
