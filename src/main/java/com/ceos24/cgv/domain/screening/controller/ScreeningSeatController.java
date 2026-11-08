package com.ceos24.cgv.domain.screening.controller;

import com.ceos24.cgv.domain.screening.dto.response.GetScreeningSeatResponse;
import com.ceos24.cgv.domain.screening.service.ScreeningSeatService;
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
public class ScreeningSeatController {

    private final ScreeningSeatService seatService;

    @Operation(summary = "좌석 조회", description = "해당 상영일정의 좌석 목록을 조회합니다.")
    @GetMapping("/api/screenings/{screeningId}/seats")
    public ResponseEntity<ApiResponse<GetScreeningSeatResponse>> getSeats(@PathVariable Long screeningId) {
        return ResponseEntity.ok(ApiResponse.success(seatService.getSeats(screeningId)));
    }
}
