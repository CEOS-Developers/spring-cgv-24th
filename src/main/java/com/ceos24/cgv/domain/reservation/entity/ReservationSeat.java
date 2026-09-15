package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.cinema.entity.Seat;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservation_seat",
                        columnNames = {"reservation_id", "seat_id"}
                )
        }
)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    public static ReservationSeat create(
            Seat seat,
            Reservation reservation
    ) {

        ReservationSeat reservationSeat = new ReservationSeat();
        reservationSeat.seat = seat;
        reservationSeat.reservation = reservation;

        return reservationSeat;
    }
}
