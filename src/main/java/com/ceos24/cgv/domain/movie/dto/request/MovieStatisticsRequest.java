package com.ceos24.cgv.domain.movie.dto.request;

public record MovieStatisticsRequest(
        Integer audienceCount,
        Double reservationRate,
        Double eggScore,
        Integer reviewCount
) {
}
