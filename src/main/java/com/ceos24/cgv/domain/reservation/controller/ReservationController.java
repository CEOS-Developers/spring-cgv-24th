package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.common.ApiResponse;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/screenings/{screeningId}")
@RestController
@Tag(name = "예매", description = "예매 관련 API")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "좌석 예매", description = "특정 상영일정의 좌석을 예매합니다.")
    @PostMapping("/seats/{seatNumber}")
    public ResponseEntity<ApiResponse<Void>> reserveSeat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long screeningId,
            @PathVariable Long seatNumber) {
        reservationService.reserveSeat(userDetails.getMemberId(), screeningId, seatNumber);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "예매 취소", description = "특정 상영일정의 좌석 예매를 취소합니다.")
    @DeleteMapping("/seats/{seatNumber}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long screeningId,
            @PathVariable Long seatNumber) {
        reservationService.cancelReservation(userDetails.getMemberId(), screeningId, seatNumber);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
