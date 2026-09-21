package com.cgvclone.cgv.domain.booking.dto;

import java.util.List;

public record BookingCreateRequest(
        Long showtimeId,
        List<SeatRequest> seats
) {
}
