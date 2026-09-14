package com.ceos24.cgv.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReservationCreateRequest(
        @NotNull Long screeningId,
        @NotNull Long userId,
        @NotEmpty @Valid List<SeatRequest> seats
) {
    public record SeatRequest(
            @NotNull @Min(1) Integer rowNum,
            @NotNull @Min(1) Integer colNum
    ) {}
}
