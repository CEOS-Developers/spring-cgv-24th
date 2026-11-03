package com.ceos24.springboot.reservation.controller;

import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ceos24.springboot.user.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
@Tag(name = "Reservation", description = "영화 예매 API")
public class ReservationController {

    private final ReservationService reservationService;

    // 영화 티켓 예매
    @Operation(
            summary = "영화 예매 대기",
            description = "결제 화면으로 이동하기 전에 호출합니다. 회차별 좌석을 잠그고 10분간 점유한 뒤, 결제 준비·인증·승인을 진행합니다."
    )
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ReservationCreateRequest request
    ) {

        ReservationResponse response =
                reservationService.createReservation(
                        userDetails.getUserId(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }



    // 예매 확인
    @Operation(
            summary = "예매 확인",
            description = "reservationId를 이용해 예매 정보를 조회합니다."
    )
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reservationId
    ) {
        return ResponseEntity.ok(
                reservationService.getReservation(
                        userDetails.getUserId(), reservationId
                )
        );
    }


    // 예매 취소
    @Operation(
            summary = "예매 취소",
            description = "reservationId를 이용해 기존 예매를 취소합니다."
    )
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reservationId
    ) {

        return ResponseEntity.ok(
                reservationService.cancelReservation(
                        userDetails.getUserId(),
                        reservationId
                )
        );
    }

    // 본인 예매 목록 조회
    @Operation(
            summary = "본인 예매 목록 조회",
            description = "로그인한 사용자의 전체 예매 내역을 조회합니다."
    )
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                reservationService.getMyReservations(
                        userDetails.getUserId()
                )
        );
    }

}
