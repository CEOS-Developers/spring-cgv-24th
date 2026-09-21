package com.spring_cgv_24th.domain.screening.dto.response;

import com.spring_cgv_24th.domain.screening.entity.Screening;
import com.spring_cgv_24th.domain.auditorium.enums.AuditoriumKind;
import java.time.LocalDateTime;

public record ScreeningResDTO(
        Long screeningId,
        Long movieId,
        String movieTitle,
        Long theaterId,
        String theaterName,
        Long auditoriumId,
        String auditoriumName,
        AuditoriumKind auditoriumKind,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int totalSeats
) {

    public static ScreeningResDTO from(Screening screening) {
        var auditorium = screening.getAuditorium();
        var type = auditorium.getType();
        return new ScreeningResDTO(
                screening.getId(),
                screening.getMovie().getId(),
                screening.getMovie().getTitle(),
                auditorium.getTheater().getId(),
                auditorium.getTheater().getName(),
                auditorium.getId(),
                auditorium.getName(),
                type.getKind(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                type.getRowCount() * type.getColumnCount());
    }
}
