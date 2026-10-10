package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentRecoveryResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationPaymentRecoveryService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 권한 검사는 SecurityConfig의 /api/admin/** 규칙이 한다.
// 스케줄러가 1분마다 하는 정리를 한 건만 바로 돌린다. 회원 문의를 받았을 때 다음 회차를 기다리지 않으려고 둔다.
@Tag(name = "예매 관리", description = "관리자 전용 예매 결제 정리")
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class ReservationAdminController {

    private final ReservationPaymentRecoveryService reservationPaymentRecoveryService;

    @Operation(summary = "결과 불명 예매 결제 정리 — 토스 조회로 확정 / 실패 / 좌석을 잃었으면 환불")
    @PostMapping("/payments/{orderId}/recovery")
    public ApiResponse<PaymentRecoveryResponse> recover(@PathVariable String orderId) {
        return ApiResponse.success(reservationPaymentRecoveryService.recover(orderId));
    }
}
