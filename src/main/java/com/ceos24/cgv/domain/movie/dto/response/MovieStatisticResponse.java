package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.entity.MovieStatistic;
import com.ceos24.cgv.domain.movie.service.MovieService;

import java.math.BigDecimal;

public record MovieStatisticResponse
        (
                Long movieStatisticId,
                Long audienceCount,
                BigDecimal reservationRate,
                BigDecimal eggRate,
                Long reviewCount
        )
{
    public static MovieStatisticResponse from(MovieStatistic movieStatistic) {
        return new MovieStatisticResponse(
                movieStatistic.getId(),
                movieStatistic.getAudienceCount(),
                movieStatistic.getReservationRate(),
                movieStatistic.getEggRate(),
                movieStatistic.getReviewCount()
        );
    }
}
