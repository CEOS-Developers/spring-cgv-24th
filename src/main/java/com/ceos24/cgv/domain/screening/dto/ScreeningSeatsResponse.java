package com.ceos24.cgv.domain.screening.dto;

import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.screening.entity.Screening;

import java.util.List;

public record ScreeningSeatsResponse(
        Long screeningId,
        int rowCount,
        int colCount,
        List<String> reservedSeats
) {
    public static ScreeningSeatsResponse from(Screening s, List<String> reservedSeats) {
        TheaterType tt = s.getTheater().getTheaterType();
        return new ScreeningSeatsResponse(s.getId(), tt.getRowCount(), tt.getColCount(), reservedSeats);
    }
}
