package com.ceos24.cgv.domain.cinema.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;

@Entity
@Getter
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seat_auditorium_position",
                        columnNames = {
                                "auditorium_id",
                                "seat_row_number",
                                "seat_column_number"
                        }
                )
        }
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auditorium_id", nullable = false)
    private Auditorium auditorium;

    @Column(name = "seat_row_number", nullable = false)
    @Min(value = 1, message = "좌석 행 번호는 1 이상이어야 합니다.")
    private int rowNumber;

    @Column(name = "seat_column_number", nullable = false)
    @Min(value = 1, message = "좌석 열 개수는 1 이상이어야 합니다.")
    private int columnNumber;

    public static Seat create(
            Auditorium auditorium,
            int rowNumber,
            int columnNumber
    ) {
        if (rowNumber <= 0 || columnNumber <= 0) {
            throw new IllegalArgumentException(
                    "좌석 행과 열은 1 이상이어야 합니다."
            );
        }

        Seat seat = new Seat();
        seat.auditorium = auditorium;
        seat.rowNumber = rowNumber;
        seat.columnNumber = columnNumber;

        return seat;
    }
}
