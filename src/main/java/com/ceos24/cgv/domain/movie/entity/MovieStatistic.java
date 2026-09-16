package com.ceos24.cgv.domain.movie.entity;

import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "movie_statistics")
public class MovieStatistic extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_statistic_id")
    private Long id;

    // 누적 관객 수
    @Column(name = "audience_count", nullable = false)
    private Long audienceCount;

    // 예매율(%)
    @Column(
            name = "reservation_rate",
            nullable = false,
            precision = 5,
            scale = 2
    )
    private BigDecimal reservationRate;

    // 에그 지수(%)
    @Column(
            name = "egg_rate",
            nullable = false,
            precision = 5,
            scale = 2
    )
    private BigDecimal eggRate;

    // 총 리뷰 수
    @Column(name = "review_count", nullable = false)
    private Long reviewCount;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "movie_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_movie_statistic_movie")
    )
    private Movie movie;

    void assignMovie(Movie movie) {
        this.movie = movie;
    }
    @Builder
    private MovieStatistic(
            Long audienceCount,
            BigDecimal reservationRate,
            BigDecimal eggRate,
            Long reviewCount
    ) {
        this.audienceCount = audienceCount;
        this.reservationRate = reservationRate;
        this.eggRate = eggRate;
        this.reviewCount = reviewCount;
    }

    //기본 통계 생성 메서드
    public static MovieStatistic createDefault() {
        return MovieStatistic.builder()
                .audienceCount(0L)
                .reservationRate(BigDecimal.ZERO)
                .eggRate(BigDecimal.ZERO)
                .reviewCount(0L)
                .build();
    }

}