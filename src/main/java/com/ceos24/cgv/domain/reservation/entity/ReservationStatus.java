package com.ceos24.cgv.domain.reservation.entity;

import java.util.List;

public enum ReservationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    public static List<ReservationStatus> seatHoldingStatuses() {
        return List.of(PENDING_PAYMENT, CONFIRMED);
    }
}
