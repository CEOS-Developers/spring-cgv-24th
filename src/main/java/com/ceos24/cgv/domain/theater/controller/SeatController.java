package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetSeatResponse;
import com.ceos24.cgv.domain.theater.service.SeatService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "좌석", description = "좌석 관련 API")
@RequiredArgsConstructor
@RequestMapping()
@RestController
public class SeatController {

    private final SeatService seatService;

    @Operation(summary = "좌석 조회", description = "해당 상영일정의 좌석 목록을 조회합니다.")
    @GetMapping("/api/screenings/{screeningId}/seats")
    public ResponseEntity<ApiResponse<GetSeatResponse>> getSeats(@PathVariable Long screeningId) {
        return ResponseEntity.ok(ApiResponse.success(seatService.getSeats(screeningId)));
    }
}
