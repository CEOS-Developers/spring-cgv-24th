package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Reservation", description = "영화 예매 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "영화 예매 API", description = "선택한 좌석으로 영화를 예매합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createReservation(
            @RequestParam Long userId,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        Long reservationId = reservationService.create(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, reservationId));
    }

    @Operation(summary = "예매 상세 조회 API", description = "특정 예매의 상세 정보를 조회합니다.")
    @GetMapping("/{reservationId}")
    public ApiResponse<ReservationResponse> getReservation(@PathVariable Long reservationId, @RequestParam Long userId) {
        return ApiResponse.onSuccess(reservationService.findById(reservationId, userId));
    }

    @Operation(summary = "내 예매 내역 조회 API", description = "특정 유저의 예매 내역 전체를 조회합니다.")
    @GetMapping
    public ApiResponse<List<ReservationResponse>> getMyReservations(@RequestParam Long userId) {
        return ApiResponse.onSuccess(reservationService.findByUserId(userId));
    }

    @Operation(summary = "예매 취소 API", description = "특정 예매를 취소합니다.")
    @PatchMapping("/{reservationId}/cancel")
    public ApiResponse<Void> cancelReservation(
            @PathVariable Long reservationId,
            @RequestParam Long userId
    ) {
        reservationService.cancel(reservationId, userId);
        return ApiResponse.onSuccess(null);
    }
}
