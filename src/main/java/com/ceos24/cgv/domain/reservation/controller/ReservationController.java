package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.payment.dto.response.PaymentReadyResponse;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import com.ceos24.cgv.global.config.SwaggerConfig;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Reservation", description = "영화 예매 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "영화 예매 API", description = "선택한 좌석으로 영화를 예매합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        Long reservationId = reservationService.create(userDetails.getUserId(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, reservationId));
    }

    @Operation(summary = "예매 상세 조회 API", description = "특정 예매의 상세 정보를 조회합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @GetMapping("/{reservationId}")
    public ApiResponse<ReservationResponse> getReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.onSuccess(reservationService.findById(reservationId, userDetails.getUserId()));
    }

    @Operation(summary = "내 예매 내역 조회 API", description = "특정 유저의 예매 내역 전체를 조회합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @GetMapping
    public ApiResponse<List<ReservationResponse>> getMyReservations(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.onSuccess(reservationService.findByUserId(userDetails.getUserId()));
    }

    @Operation(summary = "예매 결제 준비 정보 조회 API", description = "선점 중인 예매의 결제 준비 정보를 조회합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @GetMapping("/{reservationId}/payment-info")
    public ApiResponse<PaymentReadyResponse> getPaymentInfo(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.onSuccess(reservationService.getPaymentInfo(reservationId, userDetails.getUserId()));
    }

    @Operation(summary = "예매 취소 API", description = "특정 예매를 취소합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @PatchMapping("/{reservationId}/cancel")
    public ApiResponse<Void> cancelReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        reservationService.cancel(reservationId, userDetails.getUserId());
        return ApiResponse.onSuccess(null);
    }
}
