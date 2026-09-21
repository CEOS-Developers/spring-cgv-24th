package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.theater.entity.Seat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_schedule_seat",
                columnNames = {"schedule_id", "seat_id"}
        )
)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    private ReservationSeat(Reservation reservation, Schedule schedule, Seat seat) {
        this.reservation = reservation;
        this.schedule = schedule;
        this.seat = seat;
    }

    public static ReservationSeat create(Reservation reservation, Seat seat) {
        return new ReservationSeat(reservation, reservation.getSchedule(), seat);
    }
}
