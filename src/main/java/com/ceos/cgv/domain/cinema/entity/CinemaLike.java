package com.ceos.cgv.domain.cinema.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cinema_likes", uniqueConstraints = @UniqueConstraint(
        name = "uk_cinema_like_user_cinema", columnNames = {"user_id", "cinema_id"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CinemaLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cinema_like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    public CinemaLike(User user, Cinema cinema) {
        this.user = user;
        this.cinema = cinema;
    }
}
