package com.ceos24.cgv.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TheaterType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "theater_type_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int rowCount;

    @Column(nullable = false)
    private int colCount;

    @Builder
    private TheaterType(String name, int rowCount, int colCount) {
        this.name = name;
        this.rowCount = rowCount;
        this.colCount = colCount;
    }

    public int getTotalSeatCount() {
        return rowCount * colCount;
    }

    public boolean isValidSeat(int rowNum, int colNum) {
        return rowNum >= 1 && rowNum <= rowCount
                && colNum >= 1 && colNum <= colCount;
    }
}
