package com.ceos.cgv.domain.movie.entity;

import com.ceos.cgv.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movie_likes", uniqueConstraints = @UniqueConstraint(
        name = "uk_movie_like_user_movie", columnNames = {"user_id", "movie_id"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Builder
    public MovieLike(User user, Movie movie) {
        this.user = user;
        this.movie = movie;
    }
}
