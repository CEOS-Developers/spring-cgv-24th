package com.ceos24.cgv.theater.domain;

import jakarta.persistence.*;

@Entity
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Theater theater;

    @Enumerated(EnumType.STRING)
    private ScreenType screenType;
}
