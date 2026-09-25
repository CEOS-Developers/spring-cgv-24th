package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.PaymentRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
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

// principal에서 userId만 꺼내 넘긴다. 서비스가 인증 방식을 모르면 테스트나 다른 호출 경로에서
// Long 하나로 부를 수 있다.
@Tag(name = "예매", description = "예매 생성/조회/취소")
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "좌석 선점 (결제 대기 예매 생성)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody ReservationCreateRequest req) {
        return ApiResponse.success(reservationService.create(authUser.userId(), req));
    }

    @Operation(summary = "결제 (mock) — 성공 시 예매 확정, 실패 시 좌석 해제")
    @PostMapping("/{id}/payment")
    public ApiResponse<ReservationResponse> pay(@AuthenticationPrincipal AuthUser authUser,
                                                @PathVariable Long id,
                                                @Valid @RequestBody PaymentRequest req) {
        return ApiResponse.success(reservationService.pay(id, authUser.userId(), req));
    }

    @Operation(summary = "예매 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<ReservationResponse> detail(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long id) {
        return ApiResponse.success(reservationService.getById(id, authUser.userId()));
    }

    @Operation(summary = "예매 취소")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id) {
        reservationService.cancel(id, authUser.userId());
        return ApiResponse.success();
    }
}
