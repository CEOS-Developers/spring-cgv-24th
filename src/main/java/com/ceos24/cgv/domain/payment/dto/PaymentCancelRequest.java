package com.ceos24.cgv.domain.payment.dto;

public record PaymentCancelRequest(
        String cancelRequestId, String cancelReason, Long cancelAmount, Long orderItemId, Long cancelQuantity) {

    public PaymentCancelRequest(String cancelReason, Long cancelAmount, Long orderItemId, Long cancelQuantity) {
        this(null, cancelReason, cancelAmount, orderItemId, cancelQuantity);
    }
}
