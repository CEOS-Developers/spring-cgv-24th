package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    public static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private Instant reservedAt;

    @Column(nullable = false)
    private Integer totalPrice;

    // 예매 시점 좌석 스냅샷 (예: "A1, A2") — 취소 후에도 이력 조회에 사용
    @Column(nullable = false)
    private String seatSummary;

    // 좌석 선점 만료 시각 (기존 데이터 호환을 위해 nullable)
    private Instant expiresAt;

    private Reservation(User user, Schedule schedule, ReservationStatus status, Instant reservedAt,
                        Instant expiresAt, Integer totalPrice, String seatSummary) {
        this.user = user;
        this.schedule = schedule;
        this.status = status;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
        this.totalPrice = totalPrice;
        this.seatSummary = seatSummary;
    }

    public static Reservation create(User user, Schedule schedule, Integer totalPrice, String seatSummary,
                                     Instant now) {
        return new Reservation(user, schedule, ReservationStatus.PENDING_PAYMENT, now, now.plus(HOLD_DURATION),
                totalPrice, seatSummary);
    }

    public void confirmPayment() {
        if (status != ReservationStatus.PAYING) {
            throw new IllegalStateException("결제 진행 중인 예매만 확정할 수 있습니다. status: " + status);
        }
        this.status = ReservationStatus.RESERVED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public boolean isHoldExpired(Instant now) {
        return status == ReservationStatus.PENDING_PAYMENT && !now.isBefore(expiresAt);
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
