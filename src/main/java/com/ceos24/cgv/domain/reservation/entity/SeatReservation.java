package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.screening.dto.SeatStatus;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.theater.entity.Seat;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "seat_reservation",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_seat_reservation_screening_seat",
                        columnNames = {"screening_id", "seat_id"}))
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "reservation_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Reservation reservation;

    @JoinColumn(name = "screening_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Screening screening;

    @JoinColumn(name = "seat_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Seat seat;

    protected SeatReservation(Reservation reservation, Screening screening, Seat seat) {
        this.reservation = reservation;
        this.screening = screening;
        this.seat = seat;
    }

    public static SeatReservation create(Screening screening, Seat seat) {
        if (screening == null) {
            throw new IllegalArgumentException("상영 일정 정보는 필수입니다.");
        }
        if (seat == null) {
            throw new IllegalArgumentException("좌석 정보는 필수입니다.");
        }
        return new SeatReservation(null, screening, seat);
    }

    public void assignReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    public boolean isActive(LocalDateTime now) {
        ReservationStatus status = this.reservation.getStatus();
        if (status == ReservationStatus.CONFIRMED) {
            return true;
        }
        return this.reservation.isActiveHold(now);
    }

    public SeatStatus toSeatStatus(LocalDateTime now) {
        if (this.reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return SeatStatus.RESERVED;
        }
        if (this.reservation.isActiveHold(now)) {
            return SeatStatus.HOLD;
        }
        return SeatStatus.AVAILABLE;
    }
}
