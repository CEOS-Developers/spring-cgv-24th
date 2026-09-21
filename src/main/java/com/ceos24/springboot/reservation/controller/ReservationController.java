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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
@Tag(name = "Reservation", description = "영화 예매 API")
public class ReservationController {

    private final ReservationService reservationService;

    // 티켓 예매
    @Operation(
            summary = "영화 예매",
            description = "상영회차와 관람 인원, 좌석을 선택하여 영화를 예매합니다."
    )
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @RequestBody ReservationCreateRequest request
    ) {

        ReservationResponse response =
                reservationService.createReservation(request);

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
            @PathVariable Long reservationId
    ) {

        return ResponseEntity.ok(
                reservationService.getReservation(reservationId)
        );
    }


    // 예매 취소
    @Operation(
            summary = "예매 취소",
            description = "reservationId를 이용해 기존 예매를 취소합니다."
    )
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @PathVariable Long reservationId
    ) {

        return ResponseEntity.ok(
                reservationService.cancelReservation(reservationId)
        );
    }
}
