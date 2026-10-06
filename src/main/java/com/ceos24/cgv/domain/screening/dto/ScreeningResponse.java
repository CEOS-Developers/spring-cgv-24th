package com.ceos24.cgv.domain.screening.dto;

import com.ceos24.cgv.domain.screening.entity.Screening;

import java.time.LocalDateTime;

// 회차 목록 화면의 카드 한 장. 지점·상영관 종류는 상위 그룹이 갖고 있어 싣지 않는다.
// 영화는 남긴다. 극장부터 고르는 경로에서는 영화를 고르기 전까지 여러 편이 섞인다.
public record ScreeningResponse(
        Long id,
        String theaterName,
        Long movieId,
        String movieTitle,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int price,
        int remainingSeats,
        int totalSeats,
        boolean soldOut
) {
    public static ScreeningResponse from(Screening s, int occupiedSeats) {
        int totalSeats = s.getTheater().getTheaterType().getTotalSeatCount();
        int remainingSeats = totalSeats - occupiedSeats;

        return new ScreeningResponse(
                s.getId(),
                s.getTheater().getName(),
                s.getMovie().getId(),
                s.getMovie().getTitle(),
                s.getStartAt(),
                s.getEndAt(),
                s.getPrice(),
                remainingSeats,
                totalSeats,
                remainingSeats <= 0
        );
    }
}
