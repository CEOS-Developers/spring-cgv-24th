package com.ceos24.cgv.domain.theater.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "seat",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_seat_screen_number",
                        columnNames = {"screen_id", "seat_number"}))
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "screen_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Screen screen;

    @Column(name = "seat_number", nullable = false)
    private Long seatNumber;

    protected Seat(Screen screen, Long seatNumber) {
        this.screen = screen;
        this.seatNumber = seatNumber;
    }

    public static Seat create(Screen screen, Long seatNumber) {
        if (screen == null) {
            throw new IllegalArgumentException("상영관 정보는 필수입니다.");
        }
        if (seatNumber == null || seatNumber <= 0) {
            throw new IllegalArgumentException("좌석 번호는 1 이상이어야 합니다.");
        }
        return new Seat(screen, seatNumber);
    }
}
