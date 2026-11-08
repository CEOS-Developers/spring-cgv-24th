package com.ceos24.cgv.domain.payment.service.command;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancellation;

//취소 준비가 끝난 뒤, 토스에 취소 요청을 보내는 데에 필요한 값들을 묶어서 전달하는 클래스
public record FullCancellationCommand(
        Long cancellationId,
        String paymentKey,
        String reason,
        String idempotencyKey
) {
    public static FullCancellationCommand from(
            Payment payment,
            PaymentCancellation cancellation
    ) {
        return new FullCancellationCommand(
                cancellation.getId(),
                payment.getPaymentKey(),
                cancellation.getReason(),
                cancellation.getIdempotencyKey()
        );
    }
}