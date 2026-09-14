package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String genre;

    @Column(nullable = false)
    private Integer runningTime;

    @Lob
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeRating ageRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovieStatus status;

    private LocalDate openDate;

    private LocalDate closeDate;

    private Movie(String title, String genre, Integer runningTime, String description,
                  AgeRating ageRating, MovieStatus status, LocalDate openDate, LocalDate closeDate) {
        this.title = title;
        this.genre = genre;
        this.runningTime = runningTime;
        this.description = description;
        this.ageRating = ageRating;
        this.status = status;
        this.openDate = openDate;
        this.closeDate = closeDate;
    }

    public static Movie create(String title, String genre, Integer runningTime, String description,
                               AgeRating ageRating, MovieStatus status, LocalDate openDate, LocalDate closeDate) {
        return new Movie(title, genre, runningTime, description, ageRating, status, openDate, closeDate);
    }
}
