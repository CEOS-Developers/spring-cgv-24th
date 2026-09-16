package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_movies",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_movie",
                        columnNames = {
                                "user_id",
                                "movie_id"
                        }
                )
        }
)
public class UserMovie extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_movie_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Builder
    private UserMovie(
            User user,
            Movie movie
    ) {
        this.user = user;
        this.movie = movie;
    }
}