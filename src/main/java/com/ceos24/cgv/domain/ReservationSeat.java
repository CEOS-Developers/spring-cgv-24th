package com.ceos24.cgv.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_seat_screening_row_col",
        columnNames = {"screening_id", "row_num", "col_num"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @Column(name = "row_num", nullable = false)
    private int rowNum;

    @Column(name = "col_num", nullable = false)
    private int colNum;

    @Column(nullable = false)
    private int paidPrice;

    @Builder
    private ReservationSeat(Reservation reservation, Screening screening,
                            int rowNum, int colNum, int paidPrice) {
        this.reservation = reservation;
        this.screening = screening;
        this.rowNum = rowNum;
        this.colNum = colNum;
        this.paidPrice = paidPrice;
    }
}
