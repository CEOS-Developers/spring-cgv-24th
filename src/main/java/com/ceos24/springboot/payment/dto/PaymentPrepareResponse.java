package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.domain.PaymentTargetType;

import java.time.LocalDateTime;

public record PaymentPrepareResponse(
        Long paymentId,
        String orderId,
        String orderName,
        Long amount,
        PaymentStatus status,
        LocalDateTime expiresAt
) {
    public static PaymentPrepareResponse from(Payment payment) {
        return new PaymentPrepareResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getTargetType() == PaymentTargetType.RESERVATION ? "영화 티켓 예매" : "매점 상품 주문",
                payment.getAmount(),
                payment.getStatus(),
                payment.getExpiresAt()
        );
    }
}
