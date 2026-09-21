package com.ceos24.cgv.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String director;

    @Column(nullable = false, length = 50)
    private String genre;

    @Column(nullable = false)
    private int runningTime;

    @Column(nullable = false)
    private LocalDate releaseDate;

    @Column(nullable = false, length = 20)
    private String ageRating;

    @Builder
    private Movie(String title, String director, String genre,
                  int runningTime, LocalDate releaseDate, String ageRating) {
        this.title = title;
        this.director = director;
        this.genre = genre;
        this.runningTime = runningTime;
        this.releaseDate = releaseDate;
        this.ageRating = ageRating;
    }
}
