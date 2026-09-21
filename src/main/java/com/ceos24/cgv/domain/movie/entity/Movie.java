package com.ceos24.cgv.domain.movie.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Getter
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    // 정적 팩토리 메서드
    public static Movie create(String name, LocalDate releaseDate) {
        Movie movie = new Movie();
        movie.name = name;
        movie.releaseDate = releaseDate;
        return movie;
    }
}
