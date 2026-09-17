package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.request.ScreenCreateRequest;
import com.ceos24.cgv.domain.theater.service.ScreenAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "상영관 어드민", description = "상영관 어드민 관련 API")
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RestController
public class ScreenAdminController {

    private final ScreenAdminService screenAdminService;

    @Operation(summary = "상영관 생성", description = "특정 영화관에 새로운 상영관을 생성합니다.")
    @PostMapping("/theaters/{theaterId}/screens")
    public ResponseEntity<ApiResponse<Void>> createScreen(
            @PathVariable Long theaterId, @RequestBody ScreenCreateRequest request) {
        screenAdminService.createScreen(theaterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
