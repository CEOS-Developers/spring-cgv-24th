package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationCreateResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(
        name = "예매",
        description = "영화 좌석 예매 등록, 예매 내역 조회 및 예매 취소 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    // 예매 등록
    @Operation(
            summary = "결제 대기 예매 생성",
            description = "선택 좌석을 확보하고 주문번호·서버 확정 금액·결제 ID·유효시간을 반환합니다. 토스 승인 성공 후 예매가 확정됩니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationCreateResponse>> createReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        ReservationCreateResponse reservation = reservationService.createReservation(userDetails.getUsername(), request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;

        ApiResponse<ReservationCreateResponse> body = new ApiResponse<>(
                reservation,
                code.getStatus(),
                code.getMessage()
        );

        URI location = URI.create("/api/me/reservations/" + reservation.reservationId());

        return ResponseEntity.created(location).body(body);
    }

    // 사용자의 예매 목록 조회
    @Operation(
            summary = "사용자 예매 목록 조회",
            description = "사용자의 모든 예매 내역을 최근 예매 순서로 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getReservations(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<ReservationResponse> reservations = reservationService.getReservations(userDetails.getUsername());
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<ReservationResponse>> body = new ApiResponse<>(
                reservations,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 사용자의 예매 단건 조회
    @Operation(
            summary = "예매 상세 조회",
            description = "예매 ID를 이용하여 영화, 영화관, 상영관, 좌석 및 결제 금액을 조회합니다."
    )
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("reservationId") Long reservationId
    ) {
        ReservationResponse reservation = reservationService.getReservation(userDetails.getUsername(), reservationId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<ReservationResponse> body = new ApiResponse<>(
                reservation,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 예매 취소
    @Operation(
            summary = "예매 취소",
            description = "미결제 대기 예매를 취소합니다. 승인 처리 중에는 취소할 수 없고, 결제 완료 예매는 결제 취소 API를 사용해야 합니다."
    )
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("reservationId") Long reservationId
    ) {
        reservationService.cancelReservation(userDetails.getUsername(), reservationId);
        SuccessCode code = SuccessCode.DELETE_SUCCESS;

        ApiResponse<Void> body = new ApiResponse<>(
                null,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
