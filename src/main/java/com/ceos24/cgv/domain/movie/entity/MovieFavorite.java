package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "movie_favorite",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_movie_favorite_user_movie",
                        columnNames = {"user_id", "movie_id"}
                )
        }
)
public class MovieFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static MovieFavorite create(
            User user,
            Movie movie
    ) {
        MovieFavorite favorite = new MovieFavorite();
        favorite.user = user;
        favorite.movie = movie;
        favorite.createdAt = LocalDateTime.now();
        return favorite;
    }
}
