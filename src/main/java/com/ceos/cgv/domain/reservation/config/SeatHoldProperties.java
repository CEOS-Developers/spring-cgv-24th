package com.ceos.cgv.domain.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cgv.seat-hold")
public record SeatHoldProperties(Duration duration, int maxSeats, int maxActive,
                                 int cleanupBatchSize) {
}
