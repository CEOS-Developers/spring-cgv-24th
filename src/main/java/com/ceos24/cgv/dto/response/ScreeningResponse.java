package com.ceos24.cgv.dto.response;

import com.ceos24.cgv.domain.Movie;
import com.ceos24.cgv.domain.Screening;
import com.ceos24.cgv.domain.Theater;

import java.time.LocalDateTime;

public record ScreeningResponse(
        Long id,
        MovieSummary movie,
        TheaterSummary theater,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int price,
        int remainingSeats
) {
    public record MovieSummary(Long id, String title, String ageRating) {
        public static MovieSummary from(Movie m) {
            return new MovieSummary(m.getId(), m.getTitle(), m.getAgeRating());
        }
    }

    public record TheaterSummary(Long id, String name, String branchName) {
        public static TheaterSummary from(Theater t) {
            return new TheaterSummary(t.getId(), t.getName(), t.getBranch().getName());
        }
    }

    public static ScreeningResponse from(Screening s, int remainingSeats) {
        return new ScreeningResponse(
                s.getId(),
                MovieSummary.from(s.getMovie()),
                TheaterSummary.from(s.getTheater()),
                s.getStartAt(),
                s.getEndAt(),
                s.getPrice(),
                remainingSeats
        );
    }
}
