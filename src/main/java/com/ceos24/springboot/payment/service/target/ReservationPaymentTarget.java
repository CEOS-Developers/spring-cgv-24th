package com.ceos24.springboot.payment.service.target;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.payment.dto.PaymentConfirmResponse;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.service.ReservationLifecycleService;

import java.time.LocalDateTime;

record ReservationPaymentTarget(Reservation reservation, ReservationLifecycleService lifecycle) implements PaymentTarget {
    @Override
    public PaymentTargetType type() {
        return PaymentTargetType.RESERVATION;
    }

    @Override
    public Long id() {
        return reservation.getReservationId();
    }

    @Override
    public Long amount() {
        Integer price = reservation.getTotalPrice();
        return price == null ? null : price.longValue();
    }

    @Override
    public boolean isCompleted() {
        return reservation.getStatus() == ReservationStatus.Reserved;
    }

    @Override
    public boolean isHeld() {
        return reservation.getStatus() == ReservationStatus.Pending && reservation.isPaymentProcessing();
    }

    @Override
    public ErrorCode expiredError() {
        return ErrorCode.RESERVATION_EXPIRED;
    }

    @Override
    public void beginPayment(LocalDateTime now) {
        reservation.beginPayment(now);
    }

    @Override
    public void completePayment() {
        reservation.completePayment();
    }

    @Override
    public void failPayment(LocalDateTime now) {
        lifecycle.failPaymentLocked(reservation, now);
    }

    @Override
    public void validatePaid() {
        if (!isCompleted() || reservation.isPaymentProcessing()) {
            throw new GlobalException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }
    }

    @Override
    public void validateRefund(LocalDateTime now) {
        validatePaid();
        reservation.validateCancellationDeadline(now);
    }

    @Override
    public void refund() {
        lifecycle.refundLocked(reservation);
    }

    @Override
    public PaymentConfirmResponse response(Payment payment) {
        return PaymentConfirmResponse.from(payment, reservation);
    }
}
