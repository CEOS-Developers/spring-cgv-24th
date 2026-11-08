package com.spring_cgv_24th.domain.reservation.enums;

public enum ReservationStatus {
    PENDING_PAYMENT,
    PAYMENT_PROCESSING,
    PAYMENT_FAILED,
    CONFIRMED,
    PARTIALLY_CANCELLED,
    CANCELLED,
    EXPIRED
}
