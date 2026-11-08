package com.spring_cgv_24th.domain.payment.enums;

public enum PaymentStatus {
    PROCESSING,
    UNKNOWN,
    COMPLETED,
    PARTIALLY_CANCELLED,
    FAILED,
    CANCEL_PROCESSING,
    CANCEL_UNKNOWN,
    CANCEL_REJECTED_VERIFY,
    CANCELLED;

    public boolean requiresReconciliation() {
        return this == PROCESSING || this == UNKNOWN || isCancellationPending();
    }

    public boolean isCancellationPending() {
        return this == CANCEL_PROCESSING || this == CANCEL_UNKNOWN || this == CANCEL_REJECTED_VERIFY;
    }
}
