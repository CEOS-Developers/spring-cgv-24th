package com.ceos24.springboot.reservation.domain;

import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    // 여러 예매는 한 명의 사용자에게 속함
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 한번에 여러명 예매는 하나의 상영회차를 참조할 수 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id")
    private Screening screening;

    @Column(name = "child_count")
    private Integer childCount;

    @Column(name = "youth_count")
    private Integer youthCount;

    @Column(name = "adult_count")
    private Integer adultCount;

    @Column(name = "senior_count")
    private Integer seniorCount;

    @Column(name = "seat_numbers")
    private String seatNumbers;

    @Column(name = "total_price")
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ReservationStatus status;

    @Column(name = "reservation_at")
    private LocalDateTime reservationAt;

    @Builder
    public Reservation(
            User user,
            Screening screening,
            Integer childCount,
            Integer youthCount,
            Integer adultCount,
            Integer seniorCount,
            String seatNumbers,
            Integer totalPrice,
            ReservationStatus status,
            LocalDateTime reservationAt
    ) {
        this.user = user;
        this.screening = screening;
        this.childCount = childCount;
        this.youthCount = youthCount;
        this.adultCount = adultCount;
        this.seniorCount = seniorCount;
        this.seatNumbers = seatNumbers;
        this.totalPrice = totalPrice;
        this.status = status;
        this.reservationAt = reservationAt;
    }
}