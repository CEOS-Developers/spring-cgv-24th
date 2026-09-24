package com.ceos.cgv.domain.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReservationBookingRequest(
        @NotNull Long screeningId,
        @NotEmpty List<@NotNull @Valid ReservedSeatRequest> seats
) {
    public ReservationCreateRequest forUser(Long userId) {
        return new ReservationCreateRequest(userId, screeningId, seats);
    }
}
