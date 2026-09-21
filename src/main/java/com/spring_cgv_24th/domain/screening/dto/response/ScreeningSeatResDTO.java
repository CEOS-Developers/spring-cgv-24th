package com.spring_cgv_24th.domain.screening.dto.response;

import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;

public record ScreeningSeatResDTO(
        Long screeningSeatId,
        short rowNo,
        short columnNo,
        int price,
        boolean available
) {

    public static ScreeningSeatResDTO from(ScreeningSeat seat) {
        return new ScreeningSeatResDTO(
                seat.getId(),
                seat.getRowNo(),
                seat.getColumnNo(),
                seat.getPrice(),
                seat.getReservation() == null);
    }
}
