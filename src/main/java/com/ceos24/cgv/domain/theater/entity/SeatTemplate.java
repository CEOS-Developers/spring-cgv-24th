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
@Table(name = "seat_templates")
public class SeatTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_template_id")
    private Long id;

    /**
     * 좌석 행 개수
     */
    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    /**
     * 좌석 열 개수
     */
    @Column(name = "seat_column", nullable = false)
    private Integer seatColumn;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "screen_type_id",
            nullable = false,
            unique = true
    )
    private ScreenType screenType;

    @Builder
    private SeatTemplate(
            Integer seatRow,
            Integer seatColumn,
            ScreenType screenType
    ) {
        this.seatRow = seatRow;
        this.seatColumn = seatColumn;
        this.screenType = screenType;
    }

    public void update(
            Integer seatRow,
            Integer seatColumn
    ) {
        this.seatRow = seatRow;
        this.seatColumn = seatColumn;
    }

    public Integer calculateTotalSeatCount() {
        return seatRow * seatColumn;
    }
}