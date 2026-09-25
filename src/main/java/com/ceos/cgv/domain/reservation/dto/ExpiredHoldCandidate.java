package com.ceos.cgv.domain.reservation.dto;

import java.time.Instant;

public record ExpiredHoldCandidate(Long reservationId, Instant expiresAt) {
}
