package com.ceos24.spring_cgv.domain.payment.entity;

import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_seat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", length = 10, nullable = false)
    private AgeGroup ageGroup;

    @Column(name = "price", nullable = false)
    private Integer price;

    // 환불된 좌석은 그 취소 건을 가리킨다. null이면 아직 유효한(환불되지 않은) 좌석이다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_cancel_id")
    private PaymentCancel canceledBy;

    @Builder
    private PaymentSeat(Payment payment, Integer seatRow, Integer seatNumber, AgeGroup ageGroup, Integer price) {
        this.payment = payment;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;
        this.ageGroup = ageGroup;
        this.price = price;
    }

    /***
     * 함수 기능: 이 좌석이 어느 취소 건으로 환불됐는지 기록한다.
     * @param cancel 이 좌석을 환불한 취소 건
     */
    public void markCanceled(PaymentCancel cancel) {
        this.canceledBy = cancel;
    }

    public boolean isCanceled() {
        return this.canceledBy != null;
    }
}
