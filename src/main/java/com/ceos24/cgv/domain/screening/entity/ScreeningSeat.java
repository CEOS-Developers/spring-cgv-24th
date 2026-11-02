package com.ceos24.cgv.domain.screening.entity;

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
        name = "screening_seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_screening_seat",
                        columnNames = {"screening_id", "seat_id"}
                )
        }
)
public class ScreeningSeat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "reserved", nullable = false)
    private boolean reserved;

    private ScreeningSeat(Screening screening, Seat seat) {
        this.screening = screening;
        this.seat = seat;
        this.reserved = false;
    }

    public static ScreeningSeat create(Screening screening, Seat seat) {
        return new ScreeningSeat(screening, seat);
    }

    /**
     * 비관적 락으로 조회한 상태에서 호출한다.
     * 이미 점유된 좌석은 중복 예매할 수 없다.
     */
    public void reserve() {
        if (reserved) {
            throw new IllegalStateException("이미 예매된 좌석입니다.");
        }

        this.reserved = true;
    }

    /**
     * 예매 취소 시 좌석 점유를 해제하여 재예매를 허용한다.
     * 예매 좌석 이력인 ReservationSeat는 유지한다.
     */
    public void release() {
        if (!reserved) {
            throw new IllegalStateException("점유 중인 좌석이 아닙니다.");
        }

        this.reserved = false;
    }
}