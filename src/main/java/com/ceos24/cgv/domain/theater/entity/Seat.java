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
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(nullable = false)
    private Integer rowNum;

    @Column(nullable = false)
    private Integer colNum;

    private Seat(Screen screen, Integer rowNum, Integer colNum) {
        this.screen = screen;
        this.rowNum = rowNum;
        this.colNum = colNum;
    }

    public static Seat create(Screen screen, Integer rowNum, Integer colNum) {
        return new Seat(screen, rowNum, colNum);
    }
}
