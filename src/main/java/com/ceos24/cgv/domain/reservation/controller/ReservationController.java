package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.request.CancelReservationSeatsRequest;
import com.ceos24.cgv.domain.reservation.dto.request.CreateReservationRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.common.ApiResponse;
import com.ceos24.cgv.global.security.ApiUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
@Tag(name = "예매", description = "예매 관련 API")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "좌석 예매", description = "특정 상영일정의 여러 좌석을 한 번에 예매합니다.")
    @PostMapping("/screenings/{screeningId}/reservations")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserveSeats(
            @AuthenticationPrincipal ApiUserDetails userDetails,
            @PathVariable Long screeningId,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response =
                reservationService.reserveSeats(userDetails.memberId(), screeningId, request.seatNumbers());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "예매 전체 취소", description = "특정 예매 건의 모든 좌석 예매를 취소합니다.")
    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @AuthenticationPrincipal ApiUserDetails userDetails, @PathVariable Long reservationId) {
        reservationService.cancelReservation(userDetails.memberId(), reservationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "예매 부분 취소", description = "특정 예매 건에서 선택한 좌석만 부분 취소합니다.")
    @DeleteMapping("/reservations/{reservationId}/seats")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservationSeats(
            @AuthenticationPrincipal ApiUserDetails userDetails,
            @PathVariable Long reservationId,
            @Valid @RequestBody CancelReservationSeatsRequest request) {
        ReservationResponse response =
                reservationService.cancelReservationSeats(userDetails.memberId(), reservationId, request.seatNumbers());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
