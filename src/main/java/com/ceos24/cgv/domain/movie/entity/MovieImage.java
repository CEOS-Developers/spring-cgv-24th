package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(nullable = false)
    private String movieImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovieImageType type;

    private MovieImage(Movie movie, String movieImageUrl, MovieImageType type) {
        this.movie = movie;
        this.movieImageUrl = movieImageUrl;
        this.type = type;
    }

    public static MovieImage create(Movie movie, String movieImageUrl, MovieImageType type) {
        return new MovieImage(movie, movieImageUrl, type);
    }
}
