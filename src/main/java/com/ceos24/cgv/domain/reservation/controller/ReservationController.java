package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
@Tag(name = "영화 예매 API", description = "영화 예매 및 취소 API")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @Operation(summary = "영화 예매")
    public ApiResponse<ReservationResponse> createReservation(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody ReservationCreateRequest request
    ) {
        ReservationResponse response =
                reservationService.createReservation(principal.getUserId(), request);

        return ApiResponse.onSuccess("영화 예매에 성공했습니다.", response);
    }

    @DeleteMapping("/{reservationId}")
    @Operation(summary = "영화 예매 취소")
    public ApiResponse<Void> cancelReservation(
            @Parameter(description = "예매 ID")
            @PathVariable(name = "reservationId") Long reservationId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        reservationService.cancelReservation(principal.getUserId(), reservationId);

        return ApiResponse.onSuccess("영화 예매를 취소했습니다.", null);
    }
}