package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldResponse;
import com.ceos.cgv.domain.reservation.service.SeatHoldService;
import com.ceos.cgv.domain.user.security.AuthenticatedUser;
import com.ceos.cgv.global.common.dto.ApiResponse;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seat-holds")
@RequiredArgsConstructor
public class SeatHoldController {
    private final SeatHoldService seatHoldService;

    @PostMapping
    public ResponseEntity<ApiResponse<SeatHoldResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String requestKey,
            @Valid @RequestBody SeatHoldCreateRequest request) {
        UUID key;
        try {
            key = UUID.fromString(requestKey);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        SeatHoldService.HoldCreationResult result = seatHoldService.create(user.userId(), key, request);
        if (!result.created()) {
            return ResponseEntity.ok(ApiResponse.success(result.response()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/seat-holds/" + result.response().reservationId()))
                .body(ApiResponse.created(result.response()));
    }
}
