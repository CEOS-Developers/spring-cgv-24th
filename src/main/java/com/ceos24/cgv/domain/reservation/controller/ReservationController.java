package com.ceos24.cgv.domain.reservation.controller;

import com.ceos24.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "예매", description = "예매 생성/조회/취소")
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "예매 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationResponse> create(@Valid @RequestBody ReservationCreateRequest req) {
        return ApiResponse.success(reservationService.create(req));
    }

    @Operation(summary = "예매 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<ReservationResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(reservationService.getById(id));
    }

    @Operation(summary = "예매 취소")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return ApiResponse.success();
    }
}
