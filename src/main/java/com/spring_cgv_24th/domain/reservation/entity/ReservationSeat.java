package com.spring_cgv_24th.domain.reservation.entity;

import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reservation_seat",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_seat_reservation_screening_seat",
                columnNames = {"reservation_id", "screening_seat_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_seat_reservation"))
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_seat_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_seat_screening_seat"))
    private ScreeningSeat screeningSeat;

    @Column(name = "price", nullable = false, updatable = false)
    private int price;

    @Builder
    public ReservationSeat(Reservation reservation, ScreeningSeat screeningSeat, int price) {
        this.reservation = reservation;
        this.screeningSeat = screeningSeat;
        this.price = price;
    }
}
