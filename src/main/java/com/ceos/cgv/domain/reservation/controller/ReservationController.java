package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.reservation.dto.ReservationBookingRequest;
import com.ceos.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos.cgv.domain.reservation.service.ReservationService;
import com.ceos.cgv.domain.user.security.AuthenticatedUser;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "영화 예매", description = "영화 좌석 예매·조회·취소")
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    @Operation(summary = "영화 예매")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "영화 예매 성공")
    public ResponseEntity<ApiResponse<ReservationResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ReservationBookingRequest request
    ) {
        ReservationResponse response = ReservationResponse.from(
                reservationService.create(request.forUser(user.userId())));
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + response.reservationId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "예매 상세 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "예매 상세 조회 성공")
    public ResponseEntity<ApiResponse<ReservationResponse>> findById(
            @PathVariable Long reservationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(ReservationResponse.from(
                reservationService.findById(reservationId, user.userId()))));
    }

    @DeleteMapping("/{reservationId}")
    @Operation(summary = "예매 취소")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "예매 취소 성공")
    public ResponseEntity<Void> cancel(@PathVariable Long reservationId,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        reservationService.cancel(reservationId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
