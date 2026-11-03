package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        Long paymentId,
        String orderId,
        String orderName,
        Long amount,
        PaymentStatus status,
        Long reservationId,
        ReservationStatus reservationStatus,
        LocalDateTime approvedAt
) {
    public static PaymentConfirmResponse from(
            Payment payment,
            Reservation reservation
    ) {
        return new PaymentConfirmResponse(
                payment.getId(),
                payment.getOrderId(),
                "영화 티켓 예매",
                payment.getAmount(),
                payment.getStatus(),
                reservation.getReservationId(),
                reservation.getStatus(),
                payment.getApprovedAt()
        );
    }
}