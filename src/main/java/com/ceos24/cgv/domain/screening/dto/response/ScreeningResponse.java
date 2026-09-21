package com.ceos24.cgv.domain.screening.dto.response;

import com.ceos24.cgv.domain.screening.entity.Screening;

import java.time.LocalDateTime;

public record ScreeningResponse(
        Long id,
        Long movieId,
        String movieName,
        Long cinemaId,
        String cinemaName,
        Long auditoriumId,
        String auditoriumName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int price
) {

    public static ScreeningResponse from(Screening screening) {
        return new ScreeningResponse(
                screening.getId(),
                screening.getMovie().getId(),
                screening.getMovie().getName(),
                screening.getAuditorium().getCinema().getId(),
                screening.getAuditorium().getCinema().getName(),
                screening.getAuditorium().getId(),
                screening.getAuditorium().getName(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                screening.getPrice()
        );
    }
}
