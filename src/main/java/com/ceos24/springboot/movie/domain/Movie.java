package com.ceos24.springboot.movie.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(name = "movie")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "title_kr")
    private String titleKr;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", length = 10)
    private AgeRating ageRating;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "director")
    private String director;


    @Column(name = "cast_members", length = 255)
    private String castMembers;

//    Movie 객체를 만듦.
    @Builder
    public Movie(
            String titleKr,
            String titleEn,
            Integer runtimeMinutes,
            AgeRating ageRating,
            LocalDate releaseDate,
            String director,
            String castMembers
    ) {
        this.titleKr = titleKr;
        this.titleEn = titleEn;
        this.runtimeMinutes = runtimeMinutes;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.director = director;
        this.castMembers = castMembers;
    }
}