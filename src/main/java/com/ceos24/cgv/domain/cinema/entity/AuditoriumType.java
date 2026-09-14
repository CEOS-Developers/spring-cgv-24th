package com.ceos24.cgv.domain.cinema.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
public class AuditoriumType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditoriumTypeCategory category;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "column_count", nullable = false)
    private int columnCount;


    public static AuditoriumType create(
            String name,
            AuditoriumTypeCategory category,
            int rowCount,
            int columnCount
    ) {
        if (rowCount <= 0 || columnCount <= 0) {
            throw new IllegalArgumentException("좌석 행과 열은 1 이상이어야 합니다.");
        }

        AuditoriumType type = new AuditoriumType();
        type.name = name;
        type.category = category;
        type.rowCount = rowCount;
        type.columnCount = columnCount;
        return type;
    }
}
