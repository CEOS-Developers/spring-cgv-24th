package com.ceos.cgv.domain.movie.entity;

import com.ceos.cgv.domain.movie.enums.AgeRating;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "movies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "running_time", nullable = false)
    private Integer runningTime;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", nullable = false)
    private AgeRating ageRating;

    @Builder
    public Movie(
            String title,
            String description,
            Integer runningTime,
            LocalDate releaseDate,
            AgeRating ageRating
    ) {
        this.title = title;
        this.description = description;
        this.runningTime = runningTime;
        this.releaseDate = releaseDate;
        this.ageRating = ageRating;
    }
}
