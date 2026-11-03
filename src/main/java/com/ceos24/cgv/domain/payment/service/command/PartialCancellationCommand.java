package com.ceos24.cgv.domain.payment.service.command;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancellation;

public record PartialCancellationCommand(
        Long cancellationId,
        String paymentKey,
        String reason,
        int amount,
        String idempotencyKey,
        boolean alreadyCompleted
) {
    public static PartialCancellationCommand from(
            Payment payment,
            PaymentCancellation cancellation
    ) {
        return new PartialCancellationCommand(
                cancellation.getId(),
                payment.getPaymentKey(),
                cancellation.getReason(),
                cancellation.getAmount(),
                cancellation.getIdempotencyKey(),
                cancellation.getStatus() == PaymentCancellation.Status.DONE
        );
    }
}