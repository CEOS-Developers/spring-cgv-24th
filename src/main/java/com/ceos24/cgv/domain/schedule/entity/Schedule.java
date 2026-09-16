package com.ceos24.cgv.domain.schedule.entity;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.theater.entity.Screen;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer price;

    private Schedule(Movie movie, Screen screen, LocalDateTime startTime, LocalDateTime endTime, Integer price) {
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
    }

    public static Schedule create(Movie movie, Screen screen, LocalDateTime startTime, LocalDateTime endTime, Integer price) {
        return new Schedule(movie, screen, startTime, endTime, price);
    }

    // 상영 시작 전인지 (예매 가능 여부)
    public boolean isBeforeStart(LocalDateTime now) {
        return now.isBefore(this.startTime);
    }

    // 상영 시작 20분 전까지인지 (취소 가능 여부)
    public boolean isBeforeCancelDeadline(LocalDateTime now) {
        return now.isBefore(this.startTime.minusMinutes(20));
    }
}
