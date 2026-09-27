package com.ceos.cgv.domain.reservation.service.result;

import com.ceos.cgv.domain.reservation.dto.SeatHoldResponse;

import java.util.Objects;

public record HoldCreationResult(SeatHoldResponse response, boolean created) {
    public HoldCreationResult {
        Objects.requireNonNull(response);
    }
}
