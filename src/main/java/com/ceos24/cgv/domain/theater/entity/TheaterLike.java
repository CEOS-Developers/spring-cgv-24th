package com.ceos24.cgv.domain.theater.entity;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_theater_like_user_theater",
                columnNames = {"user_id", "theater_id"}
        )
)
public class TheaterLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    private TheaterLike(User user, Theater theater) {
        this.user = user;
        this.theater = theater;
    }

    public static TheaterLike create(User user, Theater theater) {
        return new TheaterLike(user, theater);
    }
}
