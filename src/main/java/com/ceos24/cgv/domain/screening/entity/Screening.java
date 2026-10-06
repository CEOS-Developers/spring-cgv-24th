package com.ceos24.cgv.domain.screening.entity;

import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.domain.movie.entity.Movie;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screening extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int price;

    @Builder
    private Screening(Theater theater, Movie movie,
                      LocalDateTime startAt, LocalDateTime endAt, int price) {
        this.theater = theater;
        this.movie = movie;
        this.startAt = startAt;
        this.endAt = endAt;
        this.price = price;
    }
}
