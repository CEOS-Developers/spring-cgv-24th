package com.spring_cgv_24th.domain.auditorium.dto;

import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import com.spring_cgv_24th.domain.auditorium.entity.AuditoriumType;
import com.spring_cgv_24th.domain.auditorium.enums.AuditoriumCategory;
import com.spring_cgv_24th.domain.auditorium.enums.AuditoriumKind;

public record AuditoriumResDTO(
        Long auditoriumId,
        String name,
        AuditoriumKind kind,
        AuditoriumCategory category,
        short rowCount,
        short columnCount,
        int totalSeats
) {

    public static AuditoriumResDTO from(Auditorium auditorium) {
        AuditoriumType type = auditorium.getType();
        return new AuditoriumResDTO(
                auditorium.getId(),
                auditorium.getName(),
                type.getKind(),
                type.getKind().getCategory(),
                type.getRowCount(),
                type.getColumnCount(),
                type.getRowCount() * type.getColumnCount());
    }
}
