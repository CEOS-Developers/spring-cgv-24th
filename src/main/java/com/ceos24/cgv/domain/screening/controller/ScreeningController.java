package com.ceos24.cgv.domain.screening.controller;

import com.ceos24.cgv.domain.screening.dto.ScreeningInfo;
import com.ceos24.cgv.domain.screening.service.ScreeningService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
@Tag(name = "상영", description = "상영 관련 API")
public class ScreeningController {

    private final ScreeningService screeningService;

    @GetMapping("/screenings/{screeningId}")
    @Operation(summary = "상영 정보 조회", description = "상영 ID로 상영 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ScreeningInfo>> getScreeningInfo(@PathVariable Long screeningId) {
        return ResponseEntity.ok(ApiResponse.success(screeningService.getScreeningInfo(screeningId)));
    }
}
