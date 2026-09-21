package com.ceos24.cgv.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false, unique = true)
    private Movie movie;

    private Integer audienceCount;

    private Double reservationRate;

    private Double eggScore;

    private Integer reviewCount;

    private MovieStatistics(Movie movie, Integer audienceCount, Double reservationRate,
                            Double eggScore, Integer reviewCount) {
        this.movie = movie;
        this.audienceCount = audienceCount;
        this.reservationRate = reservationRate;
        this.eggScore = eggScore;
        this.reviewCount = reviewCount;
    }

    public static MovieStatistics create(Movie movie, Integer audienceCount, Double reservationRate,
                                         Double eggScore, Integer reviewCount) {
        return new MovieStatistics(movie, audienceCount, reservationRate, eggScore, reviewCount);
    }

    public void update(Integer audienceCount, Double reservationRate, Double eggScore, Integer reviewCount) {
        if (audienceCount != null) this.audienceCount = audienceCount;
        if (reservationRate != null) this.reservationRate = reservationRate;
        if (eggScore != null) this.eggScore = eggScore;
        if (reviewCount != null) this.reviewCount = reviewCount;
    }
}
