package com.ceos24.cgv.domain.screening.controller;

import com.ceos24.cgv.domain.screening.dto.request.CreateScreeningRequest;
import com.ceos24.cgv.domain.screening.service.ScreeningAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Tag(name = "상영 관리", description = "관리자용 상영 관리 API")
public class ScreeningAdminController {

    private final ScreeningAdminService screeningAdminService;

    @PostMapping("/screenings")
    @Operation(summary = "상영 생성", description = "새로운 상영 일정을 생성합니다.")
    public ResponseEntity<ApiResponse<Void>> createScreening(@RequestBody CreateScreeningRequest request) {
        screeningAdminService.createScreening(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
