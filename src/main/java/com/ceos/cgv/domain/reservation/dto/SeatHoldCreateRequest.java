package com.ceos.cgv.domain.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SeatHoldCreateRequest(
        @NotNull Long screeningId,
        @NotEmpty List<@NotNull @Valid ReservedSeatRequest> seats
) {
}
