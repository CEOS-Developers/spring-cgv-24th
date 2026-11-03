package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.shop.domain.StoreOrder;
import com.ceos24.springboot.shop.domain.StoreOrderStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentConfirmResponse(
        Long paymentId,
        String orderId,
        String orderName,
        Long amount,
        PaymentStatus status,
        Long reservationId,
        ReservationStatus reservationStatus,
        LocalDateTime approvedAt,
        Long storeOrderId,
        StoreOrderStatus storeOrderStatus
) {
    public static PaymentConfirmResponse from(
            Payment payment,
            Reservation reservation
    ) {
        return new PaymentConfirmResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getOrderName(),
                payment.getAmount(),
                payment.getStatus(),
                reservation.getReservationId(),
                reservation.getStatus(),
                payment.getApprovedAt(),
                null,
                null
        );
    }

    public static PaymentConfirmResponse from(Payment payment, StoreOrder order) {
        return new PaymentConfirmResponse(payment.getId(), payment.getOrderId(), payment.getOrderName(),
                payment.getAmount(), payment.getStatus(), null, null, payment.getApprovedAt(),
                order.getOrderId(), order.getStatus());
    }
}
