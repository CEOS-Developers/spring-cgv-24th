package com.ceos.cgv.domain.movie.entity;

import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.enums.MovieVisibility;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "movies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "running_time", nullable = false)
    private Integer runningTime;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", nullable = false)
    private AgeRating ageRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16,
            columnDefinition = "varchar(16) default 'PUBLIC'")
    private MovieVisibility visibility = MovieVisibility.PUBLIC;

    @Builder
    public Movie(
            String title,
            String description,
            Integer runningTime,
            LocalDate releaseDate,
            AgeRating ageRating
    ) {
        this.title = title;
        this.description = description;
        this.runningTime = runningTime;
        this.releaseDate = releaseDate;
        this.ageRating = ageRating;
    }

    public void hide() {
        this.visibility = MovieVisibility.HIDDEN;
    }

    public void ensurePublic() {
        if (visibility != MovieVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.MOVIE_NOT_AVAILABLE);
        }
    }
}
