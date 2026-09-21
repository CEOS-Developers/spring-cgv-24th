package com.ceos24.springboot.favorite.domain;


import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@Table(name = "favorites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TheaterFavorites {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Long favoriteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id")
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder
    public TheaterFavorites(Theater theater, User user) {
        this.theater = theater;
        this.user = user;
    }
}