package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "reservation_seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservation_seat",
                        columnNames = {
                                "reservation_id",
                                "seat_id"
                        }
                )
        }
)
public class ReservationSeat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    // 가격이 변경되더라도 부분 취소는 예매 당시 금액을 기준으로 처리한다.
    @Column(name = "seat_price", nullable = false)
    private Integer seatPrice;

    // 취소 시 행을 삭제하지 않고 좌석별 취소 이력을 유지한다.
    @Column(name = "canceled", nullable = false)
    private boolean canceled;


    private ReservationSeat(Seat seat, Integer seatPrice) {
        if (seat == null || seatPrice == null || seatPrice < 0) {
            throw new IllegalArgumentException("좌석과 유효한 가격이 필요합니다.");
        }

        this.seat = seat;
        this.seatPrice = seatPrice;
        this.canceled = false;
    }
    public static ReservationSeat create(Seat seat, Integer seatPrice) {
        return new ReservationSeat(seat, seatPrice);
    }

    void assignReservation(Reservation reservation) {
        if (this.reservation != null) {
            throw new IllegalStateException("이미 예매에 연결된 좌석입니다.");
        }

        this.reservation = reservation;
    }

    // 좌석 취소는 Reservation을 통해 수행하여 예매 상태와 함께 관리한다.
    void cancel() {
        this.canceled = true;
    }
}