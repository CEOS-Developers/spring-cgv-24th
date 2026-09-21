package com.spring_cgv_24th.domain.reservation.controller;

import com.spring_cgv_24th.domain.reservation.dto.ReservationReqDTO;
import com.spring_cgv_24th.domain.reservation.dto.ReservationResDTO;
import com.spring_cgv_24th.domain.reservation.service.ReservationService;
import com.spring_cgv_24th.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reservation", description = "영화 예매,취소 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "영화 예매")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationResDTO> createReservation(
            @Valid @RequestBody ReservationReqDTO.CreateReservationDTO request) {
        return ApiResponse.onCreated(reservationService.createReservation(request));
    }

    @Operation(summary = "영화 예매 취소")
    @DeleteMapping("/{reservationId}")
    public ApiResponse<Void> cancelReservation(
            @Positive @PathVariable("reservationId") Long reservationId,
            @Parameter(description = "임시 회원 ID. 로그인 구현 후 인증된 회원 ID를 사용합니다.")
            @Positive @RequestParam("memberId") Long memberId) {
        reservationService.cancelReservation(reservationId, memberId);
        return ApiResponse.onSuccess(null);
    }
}
