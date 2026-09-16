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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class ScreeningAdminController {

    private final ScreeningAdminService screeningAdminService;

    @PostMapping("/screenings")
    public ResponseEntity<ApiResponse<Void>> createScreening(@RequestBody CreateScreeningRequest request) {
        screeningAdminService.createScreening(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
