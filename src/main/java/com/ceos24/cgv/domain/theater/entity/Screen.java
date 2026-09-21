package com.ceos24.cgv.domain.theater.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Screen {

    public Screen(Theater theater, ScreenType screenType, String name, Long totalSeats) {
        this.theater = theater;
        this.screenType = screenType;
        this.name = name;
        this.totalSeats = totalSeats;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Theater theater;

    @Enumerated(EnumType.STRING)
    private ScreenType screenType;

    private String name;

    private Long totalSeats;
}
