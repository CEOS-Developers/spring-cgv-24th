package com.ceos24.spring_cgv.domain.movie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "running_time", nullable = false)
    private Integer runningTime;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "rating", nullable = false)
    private Float rating;

    @Builder
    private Movie(String title, Integer price, Integer runningTime) {
        this.title = title;
        this.price = price;
        this.runningTime = runningTime;
        this.rating = 0.0f;
    }
}
