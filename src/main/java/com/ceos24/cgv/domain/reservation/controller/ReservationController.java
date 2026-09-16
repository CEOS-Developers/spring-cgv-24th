package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("api")
@RestController
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/screenings/{screeningId}/seats/{seatNumber}")
    public ResponseEntity<ApiResponse<Void>> reserveSeat(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long screeningId,
            @PathVariable Long seatNumber) {
        reservationService.reserveSeat(memberId, screeningId, seatNumber);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
