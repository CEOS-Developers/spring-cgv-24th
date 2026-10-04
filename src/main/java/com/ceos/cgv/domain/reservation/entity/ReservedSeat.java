package com.ceos.cgv.domain.reservation.entity;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import java.time.Instant;

@Entity
@Table(name = "reserved_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservedSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reserved_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "seat_row", nullable = false, length = 1)
    private String seatRow;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_seat_id")
    private ScreeningSeat screeningSeat;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    public boolean isCanceled() {
        return canceledAt != null;
    }

    public void cancel(Instant now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (isCanceled()) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }
        canceledAt = now;
    }

    @Builder
    public ReservedSeat(Reservation reservation, String seatRow, Integer seatNumber,
                        ScreeningSeat screeningSeat) {
        this.reservation = reservation;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;
        this.screeningSeat = screeningSeat;
    }

    public void linkScreeningSeat(ScreeningSeat screeningSeat) {
        this.screeningSeat = screeningSeat;
    }
}
