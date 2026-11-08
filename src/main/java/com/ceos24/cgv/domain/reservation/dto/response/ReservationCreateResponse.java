package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationCreateResponse(
        Long reservationId, Long paymentId, String orderId, long totalAmount,
        ReservationStatus status, LocalDateTime reservedAt, LocalDateTime expiresAt
) {
    public static ReservationCreateResponse from(Reservation reservation, Payment payment) {
        return new ReservationCreateResponse(reservation.getId(), payment.getId(), payment.getOrderId(),
                payment.getTotalAmount(), reservation.getStatus(), reservation.getReservedAt(), payment.getExpiresAt());
    }
}
