package com.spring_cgv_24th.domain.screening.entity;

import com.spring_cgv_24th.domain.reservation.entity.Reservation;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "screening_seat",
        uniqueConstraints = @UniqueConstraint(columnNames = {"screening_id", "row_no", "column_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreeningSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_screening_seat_screening"))
    private Screening screening;

    @Column(name = "row_no", nullable = false, updatable = false)
    private short rowNo;

    @Column(name = "column_no", nullable = false, updatable = false)
    private short columnNo;

    @Column(name = "price", nullable = false, updatable = false)
    private int price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", foreignKey = @ForeignKey(name = "fk_screening_seat_reservation"))
    private Reservation reservation;

    @Builder
    public ScreeningSeat(Screening screening, short rowNo, short columnNo, int price) {
        this.screening = screening;
        this.rowNo = rowNo;
        this.columnNo = columnNo;
        this.price = price;
    }

    public void occupy(Reservation reservation) {
        this.reservation = reservation;
    }

    public void release() {
        this.reservation = null;
    }
}
