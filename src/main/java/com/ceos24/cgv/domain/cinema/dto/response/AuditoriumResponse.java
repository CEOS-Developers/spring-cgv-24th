package com.ceos24.cgv.domain.cinema.dto.response;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumTypeCategory;

public record AuditoriumResponse(
        Long id,
        String name,
        Long cinemaId,
        String cinemaName,
        Long auditoriumTypeId,
        String auditoriumTypeName,
        AuditoriumTypeCategory category,
        int rowCount,
        int columnCount
) {

    public static AuditoriumResponse from(Auditorium auditorium) {
        return new AuditoriumResponse(
                auditorium.getId(),
                auditorium.getName(),
                auditorium.getCinema().getId(),
                auditorium.getCinema().getName(),
                auditorium.getAuditoriumType().getId(),
                auditorium.getAuditoriumType().getName(),
                auditorium.getAuditoriumType().getCategory(),
                auditorium.getAuditoriumType().getRowCount(),
                auditorium.getAuditoriumType().getColumnCount()
        );
    }
}
