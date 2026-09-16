package com.ceos24.cgv.domain.movie.entity;


import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "movies")
public class Movie extends BaseTimeEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Long id;

    @Column(name = "movie_title", nullable = false, length = 100)
    private String title;

    /**
     * 상영 시간(분 단위로)
     */
    @Column(name = "running_time", nullable = false)
    private Integer runningTime;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "open_date", nullable = false)
    private LocalDate openDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MovieStatus status;

    @OneToOne(
            mappedBy = "movie",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private MovieStatistic movieStatistic;

    public void assignMovieStatistic(MovieStatistic movieStatistic) {
        this.movieStatistic = movieStatistic;
        movieStatistic.assignMovie(this);
    }
    @Builder
    private Movie(
            String title,
            Integer runningTime,
            String description,
            LocalDate openDate,
            LocalDate endDate,
            MovieStatus status
    ) {
        this.title = title;
        this.runningTime = runningTime;
        this.description = description;
        this.openDate = openDate;
        this.endDate = endDate;
        this.status = status;
    }

    //영화 수정 메서드
    public void update(
            String title,
            Integer runningTime,
            String description,
            LocalDate openDate,
            LocalDate endDate,
            MovieStatus status
    ) {
        this.title = title;
        this.runningTime = runningTime;
        this.description = description;
        this.openDate = openDate;
        this.endDate = endDate;
        this.status = status;
    }

}
