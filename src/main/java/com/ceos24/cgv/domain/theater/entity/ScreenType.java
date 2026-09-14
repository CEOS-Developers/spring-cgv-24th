package com.ceos24.cgv.domain.theater.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreenType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String typeName;

    @Column(nullable = false)
    private Integer totalRow;

    @Column(nullable = false)
    private Integer totalCol;

    private ScreenType(String typeName, Integer totalRow, Integer totalCol) {
        this.typeName = typeName;
        this.totalRow = totalRow;
        this.totalCol = totalCol;
    }

    public static ScreenType create(String typeName, Integer totalRow, Integer totalCol) {
        return new ScreenType(typeName, totalRow, totalCol);
    }
}
