package com.ceos24.springboot.theater.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "screen")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screen_id")
    private Long screenId;

//  여러 screen(상영관)은 하나의 영화관(theater)에 속함.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id")
    private Theater theater;


    @Column(name = "screen_name")
    private String screenName;

    @Enumerated(EnumType.STRING)
    @Column(name = "screen_type")
    private ScreenType screenType;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "col_count")
    private Integer colCount;

    @Builder
    public Screen(
            Theater theater,
            String screenName,
            ScreenType screenType,
            Integer rowCount,
            Integer colCount
    ) {
        this.theater = theater;
        this.screenName = screenName;
        this.screenType = screenType;
        this.rowCount = rowCount;
        this.colCount = colCount;
    }

//  정적 팩토리 메서드
    public static Screen create(
            Theater theater,
            String screenName,
            ScreenType screenType,
            Integer rowCount,
            Integer colCount
    ) {
        return new Screen(
                theater,
                screenName,
                screenType,
                rowCount,
                colCount
        );
    }
}