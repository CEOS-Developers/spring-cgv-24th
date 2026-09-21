package com.ceos24.cgv.domain.screening.entity;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.movie.entity.Movie;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
public class Screening {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auditorium_id", nullable = false)
    private Auditorium auditorium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "starts_at",nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at",nullable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private int price;

    public static Screening create(
            Auditorium auditorium,
            Movie movie,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int price
    ) {
        if (startsAt.isAfter(endsAt)) {
            throw new IllegalArgumentException(
                    "시작 시간은 종료 시간보다 빨라야합니다."
            );
        }
        if (price < 0) {
            throw new IllegalArgumentException(
                    "가격은 0보다 커야합니다."
            );
        }

        Screening screening = new Screening();
        screening.auditorium = auditorium;
        screening.movie = movie;
        screening.startsAt = startsAt;
        screening.endsAt = endsAt;
        screening.price = price;

        return screening;
    }
}
