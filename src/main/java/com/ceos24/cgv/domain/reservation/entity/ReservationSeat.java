package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "reservation_seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservation_seat",
                        columnNames = {
                                "reservation_id",
                                "seat_id"
                        }
                )
        }
)
public class ReservationSeat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    private ReservationSeat(Seat seat) {
        this.seat = seat;
    }

    public static ReservationSeat create(Seat seat) {
        return new ReservationSeat(seat);
    }

    void assignReservation(Reservation reservation) {
        this.reservation = reservation;
    }
}