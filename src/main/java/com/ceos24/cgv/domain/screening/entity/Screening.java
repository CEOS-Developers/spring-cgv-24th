package com.ceos24.cgv.domain.screening.entity;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "screenings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_screening_screen_start_time",
                        columnNames = {"screen_id", "start_time"}
                )
        }
)
public class Screening extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "screening_sequence", nullable = false)
    private Integer screeningSequence;

    @Builder
    private Screening(
            Screen screen,
            Movie movie,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer screeningSequence
    ) {
        this.screen = screen;
        this.movie = movie;
        this.startTime = startTime;
        this.endTime = endTime;
        this.screeningSequence = screeningSequence;
    }

    public void update(
            Screen screen,
            Movie movie,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer screeningSequence
    ) {
        this.screen = screen;
        this.movie = movie;
        this.startTime = startTime;
        this.endTime = endTime;
        this.screeningSequence = screeningSequence;
    }
}