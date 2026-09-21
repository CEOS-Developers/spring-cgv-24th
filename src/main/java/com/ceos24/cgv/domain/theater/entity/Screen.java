package com.ceos24.cgv.domain.theater.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
