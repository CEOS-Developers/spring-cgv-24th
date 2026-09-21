package com.ceos24.cgv.domain.cinema.entity;

import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "cinema_favorite",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cinema_favorite_user_cinema",
                        columnNames = {"user_id", "cinema_id"}
                )
        }
)
public class CinemaFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static CinemaFavorite create(User user, Cinema cinema) {
        CinemaFavorite favorite = new CinemaFavorite();
        favorite.user = user;
        favorite.cinema = cinema;
        favorite.createdAt = LocalDateTime.now();
        return favorite;
    }
}
