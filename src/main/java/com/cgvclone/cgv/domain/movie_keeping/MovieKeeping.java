package com.cgvclone.cgv.domain.movie_keeping;

import com.cgvclone.cgv.common.BaseEntity;
import com.cgvclone.cgv.domain.movie.Movie;
import com.cgvclone.cgv.domain.user.User;
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
@Table(name = "movie_keepings", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_movie_keeping_user_movie",
                columnNames = {"user_id", "movie_id"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieKeeping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movieKeepingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Builder
    public MovieKeeping(User user, Movie movie) {
        this.user = user;
        this.movie = movie;
    }
}
