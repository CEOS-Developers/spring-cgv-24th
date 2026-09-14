package com.ceos24.cgv.domain.theater.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_type_id", nullable = false)
    private ScreenType screenType;

    @Column(nullable = false)
    private Integer rowNum;

    @Column(nullable = false)
    private Integer colNum;

    private Seat(ScreenType screenType, Integer rowNum, Integer colNum) {
        this.screenType = screenType;
        this.rowNum = rowNum;
        this.colNum = colNum;
    }

    public static Seat create(ScreenType screenType, Integer rowNum, Integer colNum) {
        return new Seat(screenType, rowNum, colNum);
    }
}
