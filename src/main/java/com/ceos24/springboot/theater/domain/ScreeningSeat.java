package com.ceos24.springboot.theater.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** Permanent row used to serialize initial holds for one screening and seat. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "screening_seat", uniqueConstraints = @UniqueConstraint(
        name = "uk_screening_seat_slot", columnNames = {"screening_id", "seat_id"}))
public class ScreeningSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_seat_id")
    private Long screeningSeatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_id", nullable = false, updatable = false)
    private Screening screening;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false, updatable = false)
    private Seat seat;

    private ScreeningSeat(Screening screening, Seat seat) {
        if (screening == null || seat == null || !Objects.equals(
                screening.getScreen().getScreenId(), seat.getScreenId().getScreenId())) {
            throw new IllegalArgumentException("상영회차와 좌석의 상영관이 일치해야 합니다.");
        }
        this.screening = screening;
        this.seat = seat;
    }

    public static ScreeningSeat create(Screening screening, Seat seat) {
        return new ScreeningSeat(screening, seat);
    }
}
