package com.ceos24.spring_cgv.domain.reservation.entity;

import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
import com.ceos24.spring_cgv.domain.reservation.enums.SeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "reservation_seat",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reservation_seat_active",
                columnNames = {"active_seat_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", length = 10, nullable = false)
    private AgeGroup ageGroup;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private SeatStatus status;

    // 점유 중일 때만 값이 차는 유니크 키. 점유가 풀리면 null이 되어 같은 좌석을 다시 팔 수 있다.
    // MySQL 유니크 인덱스는 null 중복을 허용하므로, 결과적으로 RESERVED인 좌석만 유일해진다.
    @Column(name = "active_seat_key", length = 64)
    private String activeSeatKey;

    @Builder
    private ReservationSeat(Reservation reservation, Screening screening, Integer seatRow, Integer seatNumber, AgeGroup ageGroup, Integer price) {
        this.reservation = reservation;
        this.screening = screening;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;
        this.ageGroup = ageGroup;
        this.price = price;
        this.status = SeatStatus.RESERVED; // 생성 시점부터 점유 상태다.
        this.activeSeatKey = activeSeatKey(screening.getId(), seatRow, seatNumber);
    }

    /***
     * 함수 기능: 좌석의 점유를 해제한다. 유니크 키를 비워 같은 좌석을 다시 판매할 수 있게 한다.
     */
    public void unreserve() {
        this.status = SeatStatus.UNRESERVED;
        this.activeSeatKey = null;
    }

    public boolean isReserved() {
        return this.status == SeatStatus.RESERVED;
    }

    /***
     * 함수 기능: 상영회차 안에서 좌석 하나를 가리키는 유니크 키를 만든다.
     * @param screeningId 상영회차 식별자
     * @param seatRow 좌석 행
     * @param seatNumber 좌석 열
     * @return {상영회차}:{행}:{열} 형식의 키
     */
    private static String activeSeatKey(Long screeningId, Integer seatRow, Integer seatNumber) {
        return screeningId + ":" + seatRow + ":" + seatNumber;
    }
}
