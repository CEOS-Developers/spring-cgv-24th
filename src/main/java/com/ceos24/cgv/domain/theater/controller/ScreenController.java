package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetScreenResponse;
import com.ceos24.cgv.domain.theater.service.ScreenService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상영관", description = "상영관 API")
@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
public class ScreenController {

    private final ScreenService screenService;

    @Operation(summary = "상영관 목록 조회", description = "특정 극장의 상영관 목록을 조회합니다.")
    @GetMapping("/theaters/{theaterId}/screens")
    public ResponseEntity<ApiResponse<GetScreenResponse>> getScreens(@PathVariable Long theaterId) {
        return ResponseEntity.ok(ApiResponse.success(screenService.getScreens(theaterId)));
    }
}
