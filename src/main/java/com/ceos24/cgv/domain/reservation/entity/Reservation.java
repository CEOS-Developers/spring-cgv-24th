package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

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
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private Instant reservedAt;

    @Column(nullable = false)
    private Integer totalPrice;

    private Reservation(User user, Schedule schedule, ReservationStatus status,
                        Instant reservedAt, Integer totalPrice) {
        this.user = user;
        this.schedule = schedule;
        this.status = status;
        this.reservedAt = reservedAt;
        this.totalPrice = totalPrice;
    }

    public static Reservation create(User user, Schedule schedule, Integer totalPrice) {
        return new Reservation(user, schedule, ReservationStatus.RESERVED, Instant.now(), totalPrice);
    }
}
