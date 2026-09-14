package com.ceos24.cgv.domain.reservation.domain;

import com.ceos24.cgv.domain.screening.domain.Screening;
import com.ceos24.cgv.domain.member.domain.Member;
import com.ceos24.cgv.domain.theater.domain.Seat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
}
