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
        this.status = ReservationStatus.PENDING_PAYMENT;
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
     * 예매 좌석 추가 :
     * 좌석별 가격을 기준으로 주문 총액을 계산한다.
     * 가격을 별도 인자로 받지 않아 좌석 금액과 총액의 불일치를 방지한다.
     */
    public void addReservationSeat(ReservationSeat reservationSeat) {

        //결제 대기 상태인지 확인
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태에서만 좌석을 추가할 수 있습니다.");
        }

        //이미 추가한 좌석인지 체크
        boolean duplicated = reservationSeats.stream()
                .anyMatch(existing ->
                        existing.getSeat().getId()
                                .equals(reservationSeat.getSeat().getId())
                );

        if (duplicated) {
            throw new IllegalArgumentException("이미 추가된 좌석입니다.");
        }

        //좌석 연결 객체에 현재 예매를 지정
        reservationSeat.assignReservation(this);
        //현재 예매의 목록에도 추가
        reservationSeats.add(reservationSeat);
        //좌석 가격을 총액에 더하기
        totalPrice += reservationSeat.getSeatPrice();
    }

    // 결제 성공 후 호출할 메서드
    /**
     * 결제 서비스가 승인 성공을 확인한 뒤 호출한다.
     */
    public void confirmPayment() {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태의 예매만 확정할 수 있습니다.");
        }

        this.status = ReservationStatus.CONFIRMED;
    }

    /**
     * 미결제 예매를 취소한다.
     * 결제된 예매는 PG 취소 성공을 확인하는 별도 흐름으로 처리한다.
     */
    public void cancel() {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태의 예매만 직접 취소할 수 있습니다.");
        }

        reservationSeats.forEach(ReservationSeat::cancel);
        this.status = ReservationStatus.CANCELED;
    }

    //ReservationSeat 행을 유지하면서 각 좌석에 취소 여부를 기록하는 메서드를 추가
    public void cancelAfterFullRefund() {
        if (status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 예매만 환불 완료 처리할 수 있습니다.");
        }

        reservationSeats.forEach(ReservationSeat::cancel);
        this.status = ReservationStatus.CANCELED;
    }

    public boolean isCanceled() {
        return status == ReservationStatus.CANCELED;
    }
}