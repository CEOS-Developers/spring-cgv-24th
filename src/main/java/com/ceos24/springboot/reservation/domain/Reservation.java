package com.ceos24.springboot.reservation.domain;

import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "reservation", indexes = {
        @Index(name = "idx_reservation_expiration", columnList = "status, expires_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    // 여러좌석 예매는 한 명의 사용자에게 속함
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 한번에 여러좌석 예매는 하나의 상영회차를 참조할 수 있음
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

    @Column(name = "total_price")
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ReservationStatus status;

    @Column(name = "payment_processing", nullable = false)
    private boolean paymentProcessing;

    @Column(name = "reservation_at")
    private LocalDateTime reservationAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder
    public Reservation(
            User user,
            Screening screening,
            Integer childCount,
            Integer youthCount,
            Integer adultCount,
            Integer seniorCount,
            Integer totalPrice,
            ReservationStatus status,
            LocalDateTime reservationAt,
            LocalDateTime expiresAt
    ) {
        this.user = user;
        this.screening = screening;
        this.childCount = childCount;
        this.youthCount = youthCount;
        this.adultCount = adultCount;
        this.seniorCount = seniorCount;
        this.totalPrice = totalPrice;
        this.status = status;
        this.reservationAt = reservationAt;
        this.expiresAt = expiresAt;
    }

    // 예매 전 점유시간 설정
    public boolean expireIfPending(LocalDateTime now) {
        // 승인 PROCESSING/UNKNOWN 동안 이 플래그를 유지한다. 기한만으로 좌석을 해제하지 않는다.
        if (paymentProcessing
                || status != ReservationStatus.Pending
                || expiresAt == null
                || expiresAt.isAfter(now)) {
            return false;
        }

        status = ReservationStatus.Expired;
        return true;
    }

    public void confirm(LocalDateTime now) {
        if (status != ReservationStatus.Pending || expiresAt == null
                || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("유효한 대기 예매만 확정할 수 있습니다.");
        }
        status = ReservationStatus.Reserved;
    }

    // 예매 취소
    public void cancel() {
        if (paymentProcessing) {
            throw new GlobalException(ErrorCode.PAYMENT_IN_PROGRESS);
        }

        if (status == ReservationStatus.Reserved) {
            throw new GlobalException(
                    ErrorCode.PAID_RESERVATION_CANCEL_NOT_SUPPORTED
            );
        }

        if (this.status == ReservationStatus.Canceled) {
            throw new IllegalArgumentException("이미 취소된 예매입니다.");
        }
        if (this.status == ReservationStatus.Expired) {
            throw new IllegalArgumentException("만료된 예매는 취소할 수 없습니다.");
        }

        this.status = ReservationStatus.Canceled;
    }

    // 결제 시작
    public void beginPayment(LocalDateTime now) {
        if (paymentProcessing) {
            throw new GlobalException(ErrorCode.PAYMENT_IN_PROGRESS);
        }

        if (status != ReservationStatus.Pending) {
            throw new GlobalException(ErrorCode.RESERVATION_NOT_PAYABLE);
        }

        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new GlobalException(ErrorCode.RESERVATION_EXPIRED);
        }

        paymentProcessing = true;
    }

    // 결제 승인
    public void completePayment() {
        if (!paymentProcessing || status != ReservationStatus.Pending) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        // 승인 시작 전에 기한을 검사했고, 처리 중에는 좌석을 유지한다.
        status = ReservationStatus.Reserved;
        paymentProcessing = false;
    }

    public void endPaymentProcessing() {
        if (!paymentProcessing || status != ReservationStatus.Pending) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }

        paymentProcessing = false;
    }

    // PG가 미승인을 확정한 뒤에만 호출한다. 실패한 결제의 좌석은 즉시 반환한다.
    public void failPayment(LocalDateTime now) {
        endPaymentProcessing();
        if (!expireIfPending(now)) {
            status = ReservationStatus.Canceled;
        }
    }

    // 전액 환불이 PG에서 확인된 뒤, 예매 락 안에서만 호출한다.
    public void cancelAfterRefund() {
        if (status != ReservationStatus.Reserved || paymentProcessing) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        status = ReservationStatus.Canceled;
    }
}
