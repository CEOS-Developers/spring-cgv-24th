package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentOrderResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.reservation.dto.ReservationCancelRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationCancelService;
import com.ceos24.cgv.domain.reservation.service.ReservationPaymentService;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.response.ApiResponse;
import com.ceos24.cgv.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// principal에서 userId만 꺼내 넘긴다. 서비스가 인증 방식을 모르면 테스트나 다른 호출 경로에서
// Long 하나로 부를 수 있다.
@Tag(name = "예매", description = "예매 생성/결제/조회/취소")
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationPaymentService reservationPaymentService;
    private final ReservationCancelService reservationCancelService;

    @Operation(summary = "좌석 선점 (결제 대기 예매 생성)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody ReservationCreateRequest req) {
        return ApiResponse.success(reservationService.create(authUser.userId(), req));
    }

    @Operation(summary = "결제 주문 생성 — 결제위젯에 넘길 orderId·금액 발급")
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentOrderResponse> createPaymentOrder(@AuthenticationPrincipal AuthUser authUser,
                                                                @PathVariable Long id) {
        return ApiResponse.success(reservationService.createPaymentOrder(id, authUser.userId()));
    }

    @Operation(summary = "결제 승인 — successUrl로 받은 값으로 토스 승인. 성공 시 예매 확정, 거절 시 선점 유지")
    @PostMapping("/{id}/payments/confirm")
    public ApiResponse<ReservationResponse> confirmPayment(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody PaymentConfirmRequest req) {
        return ApiResponse.success(reservationPaymentService.confirm(id, authUser.userId(), req));
    }

    @Operation(summary = "예매 결제 조회 — 결제 시도 목록(최근 순). 결제창만 열고 떠난 주문은 제외")
    @GetMapping("/{id}/payments")
    public ApiResponse<List<PaymentResponse>> payments(@AuthenticationPrincipal AuthUser authUser,
                                                      @PathVariable Long id) {
        return ApiResponse.success(reservationService.findPayments(id, authUser.userId()));
    }

    @Operation(summary = "내 예매 내역 (최근 예매 순, 취소·만료 포함)")
    @GetMapping
    public ApiResponse<List<ReservationResponse>> myReservations(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(reservationService.findMine(authUser.userId()));
    }

    @Operation(summary = "예매 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<ReservationResponse> detail(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long id) {
        return ApiResponse.success(reservationService.getById(id, authUser.userId()));
    }

    @Operation(summary = "예매 취소 — 결제 전 선점은 바로 취소, 결제된 예매는 남은 좌석 전부를 토스로 환불한 뒤 취소")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id) {
        reservationCancelService.cancel(id, authUser.userId());
        return ApiResponse.success();
    }

    @Operation(summary = "좌석 부분 취소 — 고른 좌석의 결제 금액만큼 토스로 환불. 결제된 예매만")
    @PostMapping("/{id}/cancellations")
    public ApiResponse<ReservationResponse> cancelSeats(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody ReservationCancelRequest req) {
        return ApiResponse.success(reservationCancelService.cancelSeats(id, authUser.userId(), req));
    }

    @Operation(summary = "취소 재시도 — 결과를 모르는 취소를 같은 멱등키로 다시 보냄. 요청 후 14일까지")
    @PostMapping("/{id}/cancellations/{cancelId}/retry")
    public ApiResponse<ReservationResponse> retryCancel(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id,
                                                        @PathVariable Long cancelId) {
        return ApiResponse.success(reservationCancelService.retry(id, authUser.userId(), cancelId));
    }
}
