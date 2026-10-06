package com.ceos24.springboot.theater.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 같은 회차에 동일 좌석 중복 막음.
@Table(name = "seat",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_screen_seat",
                        columnNames = {
                                "screen_id",
                                "row_name",
                                "seat_num"
                        }
                )
        }
)

public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screenId;

    @Column(name = "row_name", nullable = false)
    private String rowName;

    @Column(name = "seat_num", nullable = false)
    private Integer seatNum;

    private Seat(
            Screen screenId,
            String rowName,
            Integer seatNum
    ) {
        this.screenId = screenId;
        this.rowName = rowName;
        this.seatNum = seatNum;
    }

//    정적 팩토리 메서드
//    좌석 생성(좌석 예매 시)
public static Seat create(
        Screen screen,
        String rowName,
        Integer seatNum
) {

    if (screen == null) {
        throw new IllegalArgumentException(
                "상영관은 필수입니다."
        );
    }

    if (rowName == null || rowName.isBlank()) {
        throw new IllegalArgumentException(
                "좌석 행은 필수입니다."
        );
    }

    if (seatNum < 1) {
        throw new IllegalArgumentException(
                "좌석 번호는 1 이상이어야 합니다."
        );
    }

    return new Seat(
            screen,
            rowName,
            seatNum
    );
}
}