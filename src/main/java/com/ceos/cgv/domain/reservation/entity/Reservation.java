package com.ceos.cgv.domain.reservation.entity;

import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.entity.BaseTimeEntity;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_user_request_key",
                columnNames = {"user_id", "request_key"}),
        indexes = @Index(name = "idx_reservation_status_expires_at",
                columnList = "status, expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "request_key", length = 36)
    private String requestKey;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservedSeat> reservedSeats = new ArrayList<>();

    @lombok.Builder
    public Reservation(User user, Screening screening) {
        this.user = user;
        this.screening = screening;
        this.status = ReservationStatus.RESERVED;
    }

    public static Reservation hold(User user, Screening screening, UUID requestKey, Instant expiresAt) {
        Reservation reservation = new Reservation(user, screening);
        reservation.status = ReservationStatus.HELD;
        reservation.requestKey = requestKey.toString();
        reservation.expiresAt = expiresAt;
        return reservation;
    }

    public boolean isExpiredAt(Instant now) {
        return status == ReservationStatus.HELD && !now.isBefore(expiresAt);
    }

    public void confirm(Instant now) {
        if (status == ReservationStatus.RESERVED) {
            return;
        }
        if (status != ReservationStatus.HELD) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        if (isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }
        status = ReservationStatus.RESERVED;
    }

    public void release() {
        if (status != ReservationStatus.HELD) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        status = ReservationStatus.RELEASED;
    }

    public void expire(Instant now) {
        if (!isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        status = ReservationStatus.EXPIRED;
    }

    public void addReservedSeat(ReservedSeat reservedSeat) {
        if (reservedSeat == null || reservedSeat.getReservation() != this) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String row = reservedSeat.getSeatRow();
        Integer number = reservedSeat.getSeatNumber();
        if (row == null || !row.matches("[A-Z]") || number == null || number < 1) {
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        }
        ScreeningSeat linked = reservedSeat.getScreeningSeat();
        if (linked != null && (!row.equals(linked.getSeatRow()) || !number.equals(linked.getSeatNumber())
                || !sameScreening(linked.getScreening()))) {
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        }
        if (reservedSeats.stream().anyMatch(seat -> row.equals(seat.getSeatRow())
                && number.equals(seat.getSeatNumber()))) {
            throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        }
        reservedSeats.add(reservedSeat);
    }

    public void addSeat(String row, Integer number, ScreeningSeat screeningSeat) {
        addReservedSeat(new ReservedSeat(this, row, number, screeningSeat));
    }

    public List<ReservedSeat> getReservedSeats() {
        return Collections.unmodifiableList(reservedSeats);
    }

    private boolean sameScreening(Screening other) {
        return screening == other || (screening != null && other != null
                && screening.getId() != null && screening.getId().equals(other.getId()));
    }

    public void cancel() {
        if (status == ReservationStatus.CANCELED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }
        if (status != ReservationStatus.RESERVED) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        this.status = ReservationStatus.CANCELED;
    }
}
