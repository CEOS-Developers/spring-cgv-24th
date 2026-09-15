package com.ceos.cgv.domain.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReservationCreateRequest(
        @NotNull Long userId,
        @NotNull Long screeningId,
        @NotEmpty List<@Valid ReservedSeatRequest> seats
) {
}
