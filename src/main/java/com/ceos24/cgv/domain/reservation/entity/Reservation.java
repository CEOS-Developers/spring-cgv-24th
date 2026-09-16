package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reservations")
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    /**
     * 예매 당시 전체 결제 금액
     */
    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(
            mappedBy = "reservation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private final List<ReservationSeat> reservationSeats =
            new ArrayList<>();

    private Reservation(
            User user,
            Screening screening
    ) {
        this.user = user;
        this.screening = screening;
        this.totalPrice = 0;
        this.status = ReservationStatus.RESERVED;
    }

    public static Reservation create(
            User user,
            Screening screening
    ) {
        return new Reservation(
                user,
                screening
        );
    }

    /**
     * 예매 좌석 추가
     */
    public void addReservationSeat(
            ReservationSeat reservationSeat,
            Integer seatPrice
    ) {
        reservationSeats.add(reservationSeat);
        reservationSeat.assignReservation(this);

        totalPrice += seatPrice;
    }

    /**
     * 예매 취소
     */
    //취소하더라도 좌석 삭제 안함-> 취소된 예매가 어떤 좌석인지 알 수 있음.
    public void cancel() {
        this.status = ReservationStatus.CANCELED;
    }

    public boolean isCanceled() {
        return status == ReservationStatus.CANCELED;
    }
}