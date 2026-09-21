package com.cgvclone.cgv.domain.movie;

import com.cgvclone.cgv.domain.movie_genre.MovieGenre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movieId;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContentRating contentRatingCode;

    @Column(nullable = false)
    private Integer runningTime;

    private LocalDate releaseDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String posterUrl;

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private List<MovieGenre> movieGenres;
}
