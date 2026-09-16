package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetScreenResponse;
import com.ceos24.cgv.domain.theater.service.ScreenService;
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
public class ScreenController {

    private final ScreenService screenService;

    @GetMapping("/theaters/{theaterId}/screens")
    public ResponseEntity<ApiResponse<GetScreenResponse>> getScreens(@PathVariable Long theaterId) {
        return ResponseEntity.ok(ApiResponse.success(screenService.getScreens(theaterId)));
    }
}
