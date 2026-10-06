package com.ceos.cgv.domain.reservation.dto;

import com.ceos.cgv.domain.reservation.enums.ReservationStatus;

import java.time.Instant;

public record ReservationSnapshot(Long userId, Long screeningId, Long movieId,
                                  ReservationStatus status, Instant expiresAt) {
}
