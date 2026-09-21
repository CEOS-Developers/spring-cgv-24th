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
    // 좌석을 개별 행으로 저장하지 않고 행·열 크기만 보관한다.
    // 요구사항상 상영관 종류가 같으면 배치가 동일하고 직사각형이 보장되기 때문이다.

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

    // 좌석 범위 검증은 DB가 해주지 못하므로 도메인에서 책임진다.
    public boolean isValidSeat(int rowNum, int colNum) {
        return rowNum >= 1 && rowNum <= rowCount
                && colNum >= 1 && colNum <= colCount;
    }
}
