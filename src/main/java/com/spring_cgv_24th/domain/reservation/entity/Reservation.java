package com.spring_cgv_24th.domain.reservation.entity;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.payment.target.PaymentTarget;
import com.spring_cgv_24th.domain.reservation.enums.ReservationStatus;
import com.spring_cgv_24th.domain.screening.entity.Screening;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "reservation", indexes = @Index(name = "idx_reservation_expiration", columnList = "status, expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation implements PaymentTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_screening"))
    private Screening screening;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'CONFIRMED'")
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "reserved_at", nullable = false, updatable = false, columnDefinition = "timestamp(6)")
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    private LocalDateTime reservedAt = LocalDateTime.now();

    @Column(name = "cancelled_at", columnDefinition = "timestamp(6)")
    private LocalDateTime cancelledAt;

    @Column(name = "total_price", nullable = false, updatable = false)
    private Long totalPrice;

    @Column(name = "expires_at", updatable = false, columnDefinition = "timestamp(6)")
    private LocalDateTime expiresAt;

    @Column(name = "payment_order_id", unique = true, updatable = false, length = 64)
    private String paymentOrderId;

    @Builder
    public Reservation(Member member, Screening screening, long totalPrice, LocalDateTime reservedAt) {
        if (member == null || screening == null || totalPrice <= 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        this.member = member;
        this.screening = screening;
        this.totalPrice = totalPrice;
        this.reservedAt = reservedAt;
        this.status = ReservationStatus.PENDING_PAYMENT;
        this.expiresAt = reservedAt.plusMinutes(10);
        this.paymentOrderId = UUID.randomUUID().toString();
    }

    public void validateCancellation(LocalDateTime now) {
        if (status == ReservationStatus.CANCELLED || status == ReservationStatus.EXPIRED) {
            throw new CustomException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
        }
        if (status == ReservationStatus.PAYMENT_PROCESSING
                || status == ReservationStatus.PARTIALLY_CANCELLED
                || (status == ReservationStatus.CONFIRMED && paymentOrderId != null)) {
            throw new CustomException(ErrorCode.RESERVATION_PAYMENT_CANCELLATION_REQUIRED);
        }
        screening.validateCancellationTime(now);
    }

    public void cancel(LocalDateTime cancelledAt) {
        validateCancellation(cancelledAt);
        this.cancelledAt = cancelledAt;
        this.status = ReservationStatus.CANCELLED;
    }

    public void beginPayment(LocalDateTime now) {
        if (status != ReservationStatus.PENDING_PAYMENT || !now.isBefore(expiresAt)) {
            throw new CustomException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }
        screening.validateReservationTime(now);
        status = ReservationStatus.PAYMENT_PROCESSING;
    }

    public void completePayment() {
        status = ReservationStatus.CONFIRMED;
    }

    public void failPayment() {
        status = ReservationStatus.PAYMENT_FAILED;
    }

    public void validatePaymentCancellation(LocalDateTime now) {
        if (status != ReservationStatus.CONFIRMED && status != ReservationStatus.PARTIALLY_CANCELLED) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELLABLE);
        }
        screening.validateCancellationTime(now);
    }

    public void cancelPayment(LocalDateTime now) {
        cancelledAt = now;
        status = ReservationStatus.CANCELLED;
    }

    public void partiallyCancelPayment() {
        status = ReservationStatus.PARTIALLY_CANCELLED;
    }

    public boolean releaseSeats(LocalDateTime now) {
        if (status == ReservationStatus.PAYMENT_FAILED) {
            status = ReservationStatus.CANCELLED;
        } else if (status == ReservationStatus.PENDING_PAYMENT && !now.isBefore(expiresAt)) {
            status = ReservationStatus.EXPIRED;
        } else {
            return false;
        }
        cancelledAt = now;
        return true;
    }
}
