package com.ceos24.cgv.screening.domain;

import com.ceos24.cgv.movie.domain.Movie;
import com.ceos24.cgv.theater.domain.Screen;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Movie movie;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Screen screen;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
