package com.ceos24.cgv.domain.screening.entity;

import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.domain.movie.entity.Movie;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
// 회차 검색은 "이 영화의 이 날짜"를 찾는다. 외래 키 인덱스(movie_id)만으로는 그 영화의 모든 날짜를 읽고 거른다.
// 등호 조건을 앞에, 범위 조건을 뒤에 둔다. 왼쪽 접두가 movie_id라 외래 키 인덱스 역할도 대신한다.
@Table(indexes = @Index(name = "idx_screening_movie_start", columnList = "movie_id, start_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screening extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int price;

    @Builder
    private Screening(Theater theater, Movie movie,
                      LocalDateTime startAt, LocalDateTime endAt, int price) {
        this.theater = theater;
        this.movie = movie;
        this.startAt = startAt;
        this.endAt = endAt;
        this.price = price;
    }
}
