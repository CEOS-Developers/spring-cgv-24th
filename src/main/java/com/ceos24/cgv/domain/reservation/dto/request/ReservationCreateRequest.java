package com.ceos24.cgv.domain.reservation.dto.request;

import java.util.List;

public record ReservationCreateRequest(
        Long screeningId,
        List<Long> seatIds
) {
}