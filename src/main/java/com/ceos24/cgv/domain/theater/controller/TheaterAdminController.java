package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.request.CreateTheaterRequest;
import com.ceos24.cgv.domain.theater.service.TheaterAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RestController
public class TheaterAdminController {

    private final TheaterAdminService theaterAdminService;

    @PostMapping("/theaters")
    public ResponseEntity<ApiResponse<Void>> createTheater(@RequestBody CreateTheaterRequest request) {
        theaterAdminService.createTheater(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
