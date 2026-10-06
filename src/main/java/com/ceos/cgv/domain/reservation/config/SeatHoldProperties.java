package com.ceos.cgv.domain.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cgv.seat-hold")
public record SeatHoldProperties(Duration duration, int maxSeats, int maxActive,
                                 int cleanupBatchSize) {
    public SeatHoldProperties {
        if (duration == null || duration.isZero() || duration.isNegative()
                || maxSeats < 1 || maxActive < 1 || cleanupBatchSize < 1) {
            throw new IllegalArgumentException("Seat hold settings must be positive");
        }
    }
}
