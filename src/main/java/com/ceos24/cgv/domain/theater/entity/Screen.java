package com.ceos24.cgv.domain.theater.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_type_id", nullable = false)
    private ScreenType screenType;

    @Column(nullable = false)
    private String name;

    private Screen(Theater theater, ScreenType screenType, String name) {
        this.theater = theater;
        this.screenType = screenType;
        this.name = name;
    }

    public static Screen create(Theater theater, ScreenType screenType, String name) {
        return new Screen(theater, screenType, name);
    }

    public List<Seat> createSeats() {
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= screenType.getTotalRow(); row++) {
            for (int col = 1; col <= screenType.getTotalCol(); col++) {
                seats.add(Seat.create(this, row, col));
            }
        }
        return seats;
    }
}
