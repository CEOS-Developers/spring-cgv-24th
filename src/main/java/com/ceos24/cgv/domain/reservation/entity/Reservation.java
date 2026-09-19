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

    public Reservation(Member member, Seat seat) {
        this.member = member;
        this.seat = seat;
        createdAt = LocalDateTime.now();
    }

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
}
