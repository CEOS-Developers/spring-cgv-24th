package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.theater.entity.Seat;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @JoinColumn
    @OneToOne(fetch = FetchType.LAZY)
    private Seat seat;

    private LocalDateTime createdAt;

    protected Reservation(Member member, Seat seat) {
        this.member = member;
        this.seat = seat;
        this.createdAt = LocalDateTime.now();
    }

    public static Reservation create(Member member, Seat seat) {
        validateReservation(member, seat);
        return new Reservation(member, seat);
    }

    private static void validateReservation(Member member, Seat seat) {
        if (member == null) {
            throw new IllegalArgumentException("회원 정보는 필수입니다.");
        }
        if (seat == null) {
            throw new IllegalArgumentException("좌석 정보는 필수입니다.");
        }
    }

    public void cancel() {
        if (this.seat != null) {
            this.seat.cancelReservation();
        }
    }
}
