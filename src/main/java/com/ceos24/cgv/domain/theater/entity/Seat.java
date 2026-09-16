package com.ceos24.cgv.domain.theater.entity;

import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seat_screen_row_column",
                        //서로 다른 상영관에 A1 좌석 가능, 같은 상영관 같은 좌석 두개 생성 방지
                        columnNames = {
                                "screen_id",
                                "seat_row",
                                "seat_column"
                        }
                )
        }
)
public class Seat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long id;

    /**
     * 좌석 행 예시 - A, B ,C
     */
    @Column(name = "seat_row", nullable = false, length = 10)
    private String seatRow;

    /**
     * 좌석 열 예시 - 1, 2, 3 ( 좌석은 A1, A2 이런식 !! )
     */
    @Column(name = "seat_column", nullable = false)
    private Integer seatColumn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Builder
    private Seat(
            String seatRow,
            Integer seatColumn,
            Screen screen
    ) {
        this.seatRow = seatRow;
        this.seatColumn = seatColumn;
        this.screen = screen;
    }
}