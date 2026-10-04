package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.payment.controller.PaymentController;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationStatus;
import com.ceos.cgv.domain.payment.service.PaymentWorkflowService;
import com.ceos.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos.cgv.domain.reservation.service.ReservationService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import com.ceos.cgv.global.security.principal.CgvUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "영화 예매", description = "영화 좌석 예매·조회·취소")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {
    private final ReservationService reservationService;
    private final PaymentWorkflowService payments;

    @GetMapping("/{reservationId}")
    @Operation(summary = "예매 상세 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "예매 상세 조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "본인 예매만 조회할 수 있습니다.")
    public ResponseEntity<ApiResponse<ReservationResponse>> findById(
            @PathVariable Long reservationId, @AuthenticationPrincipal CgvUserDetails user) {
        return ResponseEntity.ok(
                ApiResponse.success(reservationService.findById(reservationId, user.userId())));
    }

    @DeleteMapping("/{reservationId}")
    @Operation(summary = "예매 취소")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "예매 취소 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "본인 예매만 취소할 수 있습니다.")
    public ResponseEntity<?> cancel(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal CgvUserDetails user,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        PaymentOperationResponse result =
                payments.cancelReservation(
                        user.userId(),
                        reservationId,
                        key == null ? null : PaymentController.requestKey(key));
        if (result == null || result.status() == PaymentOperationStatus.SUCCEEDED)
            return ResponseEntity.noContent().build();
        return PaymentController.response(result);
    }
}
