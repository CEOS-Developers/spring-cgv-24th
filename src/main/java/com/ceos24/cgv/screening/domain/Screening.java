package com.ceos24.cgv.screening.domain;

import com.ceos24.cgv.movie.domain.Movie;
import com.ceos24.cgv.theater.domain.Screen;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Movie movie;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Screen screen;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
