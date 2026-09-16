package com.cgvclone.cgv.domain.Booking.dto;

import java.util.List;

public record BookingCreateRequest(
        Long showtimeId,
        List<SeatRequest> seats
) {
}
