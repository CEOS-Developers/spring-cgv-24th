package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
@Tag(
        name = "영화 예매 API",
        description = "영화 예매 및 취소 API"
)
public class ReservationController {

    private final ReservationService reservationService;

    // 1. 영화 예매
    @PostMapping
    @Operation(
            summary = "영화 예매",
            description = "특정 상영 일정의 좌석을 선택하여 영화를 예매합니다."
    )
    public ApiResponse<ReservationResponse> createReservation(
            @Parameter(description = "사용자 ID")
            @RequestParam(name = "userId")
            Long userId,

            @RequestBody
            ReservationCreateRequest request
    ) {
        ReservationResponse response =
                reservationService.createReservation(
                        userId,
                        request
                );

        return ApiResponse.onSuccess(
                "영화 예매에 성공했습니다.",
                response
        );
    }

    // 2. 영화 예매 취소
    @DeleteMapping("/{reservationId}")
    @Operation(
            summary = "영화 예매 취소",
            description = "사용자의 영화 예매를 취소합니다."
    )
    public ApiResponse<Void> cancelReservation(
            @Parameter(description = "예매 ID")
            @PathVariable(name = "reservationId")
            Long reservationId,

            @Parameter(description = "사용자 ID")
            @RequestParam(name = "userId")
            Long userId
    ) {
        reservationService.cancelReservation(
                userId,
                reservationId
        );

        return ApiResponse.onSuccess(
                "영화 예매 취소에 성공했습니다.",
                null
        );
    }
}