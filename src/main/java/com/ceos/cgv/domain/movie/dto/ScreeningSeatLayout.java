package com.ceos.cgv.domain.movie.dto;

import com.ceos.cgv.domain.movie.enums.MovieVisibility;

/** 좌석 조회에 필요한 상영 정보만 담는 내부 조회 결과이며 API 응답이 아니다. */
public record ScreeningSeatLayout(MovieVisibility movieVisibility, Integer rowCount, Integer seatsPerRow) {
    public long expectedSeatCount() {
        return (long) rowCount * seatsPerRow;
    }
}
