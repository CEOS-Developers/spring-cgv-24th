package com.ceos24.springboot.reservation.dto;

import java.util.List;

public record ReservationCreateRequest(
        Long screeningId,
        Integer childCount,
        Integer youthCount,
        Integer adultCount,
        Integer seniorCount,
        List<Long> seatIds
) {
}