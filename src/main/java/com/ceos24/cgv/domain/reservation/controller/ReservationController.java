package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    // 예매 등록
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createReservation(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        Long reservationId = reservationService.createReservation(userId, request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;

        ApiResponse<Long> body = new ApiResponse<>(
                reservationId,
                code.getStatus(),
                code.getMessage()
        );

        URI location = URI.create("/api/users/" + userId + "/reservations/" + reservationId);

        return ResponseEntity.created(location).body(body);
    }

    // 사용자의 예매 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservations(
            @PathVariable("userId") Long userId
    ) {
        List<ReservationResponse> reservations = reservationService.getReservations(userId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<ReservationResponse>> body = new ApiResponse<>(
                reservations,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 사용자의 예매 단건 조회
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(
            @PathVariable("userId") Long userId,
            @PathVariable("reservationId") Long reservationId
    ) {
        ReservationResponse reservation = reservationService.getReservation(userId, reservationId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<ReservationResponse> body = new ApiResponse<>(
                reservation,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 예매 취소
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @PathVariable("userId") Long userId,
            @PathVariable("reservationId") Long reservationId
    ) {
        reservationService.cancelReservation(userId, reservationId);
        SuccessCode code = SuccessCode.DELETE_SUCCESS;

        ApiResponse<Void> body = new ApiResponse<>(
                null,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
