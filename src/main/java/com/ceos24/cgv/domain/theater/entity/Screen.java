package com.ceos24.cgv.domain.theater.entity;

import com.ceos24.cgv.domain.theater.enums.TheaterType;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "screens")
public class Screen extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screen_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    //상영관 이름
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "theater_type", nullable = false, length = 20)
    private TheaterType theaterType;

    @Column(name = "seat_num", nullable = false)
    private Integer seatCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_type_id", nullable = false)
    private ScreenType screenType;

    @Builder
    private Screen(
            Theater theater,
            String name,
            TheaterType theaterType,
            Integer seatCount,
            ScreenType screenType

    ) {
        this.theater = theater;
        this.name = name;
        this.theaterType = theaterType;
        this.seatCount = seatCount;
        this.screenType = screenType;
    }

    public void update(
            TheaterType theaterType,
            String name,
            Integer seatCount,
            ScreenType screenType
    ) {
        this.theaterType = theaterType;
        this.name = name;
        this.seatCount = seatCount;
        this.screenType = screenType;
    }
}