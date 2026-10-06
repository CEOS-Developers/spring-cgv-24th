package com.ceos24.springboot.reservation.domain;

import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.theater.domain.Seat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reservation_seat",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_screening_seat",
                        columnNames = {
                                "screening_id",
                                "seat_id"
                        }
                )
        }
)

public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_seat_id")
    private Long id;

//  예매 당 여러 좌석 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

//  해당 상영관의 좌석과 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    private ReservationSeat(
            Reservation reservation,
            Screening screening,
            Seat seat
    ) {
        this.reservation = reservation;
        this.screening = screening;
        this.seat = seat;
    }

    public static ReservationSeat create(
            Reservation reservation,
            Screening screening,
            Seat seat
    ) {
        return new ReservationSeat(
                reservation,
                screening,
                seat
        );
    }
}