package com.ceos24.springboot.reservation.dto;

public record ReservationCreateRequest(
        Long screeningId,
        Integer childCount,
        Integer youthCount,
        Integer adultCount,
        Integer seniorCount,
        String seatNumbers
) {
}