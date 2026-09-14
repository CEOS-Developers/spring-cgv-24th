package com.ceos24.cgv.domain.review.entity;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.review.enums.ReviewRating;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewRating rating;

    @Lob
    private String content;

    private Review(User user, Movie movie, ReviewRating rating, String content) {
        this.user = user;
        this.movie = movie;
        this.rating = rating;
        this.content = content;
    }

    public static Review create(User user, Movie movie, ReviewRating rating, String content) {
        return new Review(user, movie, rating, content);
    }
}
