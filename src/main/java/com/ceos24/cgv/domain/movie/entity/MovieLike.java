package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    private MovieLike(User user, Movie movie) {
        this.user = user;
        this.movie = movie;
    }

    public static MovieLike create(User user, Movie movie) {
        return new MovieLike(user, movie);
    }
}
