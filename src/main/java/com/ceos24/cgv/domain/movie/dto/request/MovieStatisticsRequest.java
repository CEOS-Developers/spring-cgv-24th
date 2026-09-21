package com.ceos24.cgv.domain.movie.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

public record MovieStatisticsRequest(
        @PositiveOrZero(message = "누적관객수는 0 이상이어야 합니다.")
        Integer audienceCount,

        @DecimalMin(value = "0.0", message = "예매율은 0 이상이어야 합니다.")
        @DecimalMax(value = "100.0", message = "예매율은 100 이하이어야 합니다.")
        Double reservationRate,

        @DecimalMin(value = "0.0", message = "에그지수는 0 이상이어야 합니다.")
        @DecimalMax(value = "100.0", message = "에그지수는 100 이하이어야 합니다.")
        Double eggScore,

        @PositiveOrZero(message = "리뷰 수는 0 이상이어야 합니다.")
        Integer reviewCount
) {
}
