package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity userEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public static Reservation create(
            Screening screening,
            UserEntity userEntity
    ) {
        if (screening == null || userEntity == null) {
            throw new IllegalArgumentException("상영 회차와 예매자는 필수입니다.");
        }
        Reservation reservation = new Reservation();
        reservation.screening = screening;
        reservation.userEntity = userEntity;
        reservation.status = ReservationStatus.PENDING_PAYMENT;
        reservation.reservedAt = LocalDateTime.now();
        reservation.cancelledAt = null;

        return reservation;
    }

    // 결제 승인 결과를 반영하는 트랜잭션에서만 확정한다.
    public void confirm() {
        requirePending();
        status = ReservationStatus.CONFIRMED;
        confirmedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status != ReservationStatus.PENDING_PAYMENT && status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("취소할 수 있는 예매가 아닙니다.");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void expire() {
        requirePending();
        status = ReservationStatus.EXPIRED;
    }

    private void requirePending() {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 예매가 아닙니다.");
        }
    }
}
