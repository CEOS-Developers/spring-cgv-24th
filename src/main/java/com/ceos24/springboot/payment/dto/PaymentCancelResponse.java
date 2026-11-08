package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.payment.domain.*;
import java.time.LocalDateTime;

public record PaymentCancelResponse(
        Long cancellationId, Long paymentId, String requestId,
        Long cancelAmount, String cancelReason, PaymentCancellationStatus cancelStatus,
        Long balanceAmount, PaymentStatus paymentStatus,
        LocalDateTime createdAt, LocalDateTime canceledAt
) {
    public static PaymentCancelResponse from(Payment payment, PaymentCancellation cancellation) {
        return new PaymentCancelResponse(cancellation.getId(), payment.getId(), cancellation.getRequestId(),
                cancellation.getCancelAmount(), cancellation.getCancelReason(), cancellation.getStatus(),
                payment.getBalanceAmount(), payment.getStatus(), cancellation.getCreatedAt(), cancellation.getCanceledAt());
    }
}
