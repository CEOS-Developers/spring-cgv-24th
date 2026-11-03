package com.ceos24.cgv.domain.payment.dto.response;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;

import java.time.OffsetDateTime;

public record PaymentDetailResponse(
        String orderId,
        Long reservationId,
        PaymentStatus paymentStatus,
        ReservationStatus reservationStatus,
        //최초 주문 금액
        Integer amount,
        // 부분 취소 결과
        Integer canceledAmount,
        OffsetDateTime approvedAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        var reservation = payment.getReservation();

        return new PaymentDetailResponse(
                payment.getOrderId(),
                reservation.getId(),
                payment.getStatus(),
                reservation.getStatus(),
                payment.getAmount(),
                payment.getCanceledAmount(),
                payment.getApprovedAt()
        );
    }
}