package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;

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
                "영화 티켓 예매", // 영화티켓예매로 지정해놓고 나중에 영화명으로 수정할 예정.
                payment.getAmount(),
                payment.getStatus(),
                payment.getExpiresAt()
        );
    }
}