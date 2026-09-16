package com.ceos24.cgv.domain.screening.controller;

import com.ceos24.cgv.domain.screening.dto.ScreeningInfo;
import com.ceos24.cgv.domain.screening.service.ScreeningService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
public class ScreeningController {

    private final ScreeningService screeningService;

    @GetMapping("/screenings/{screeningId}")
    public ResponseEntity<ApiResponse<ScreeningInfo>> getScreeningInfo(@PathVariable Long screeningId) {
        return ResponseEntity.ok(ApiResponse.success(screeningService.getScreeningInfo(screeningId)));
    }
}
