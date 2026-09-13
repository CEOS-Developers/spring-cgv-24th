package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetTheaterResponse;
import com.ceos24.cgv.domain.theater.service.TheaterService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/theaters")
@RequiredArgsConstructor
@RestController
public class TheaterController {

    private final TheaterService theaterService;

    @GetMapping
    public ResponseEntity<ApiResponse<GetTheaterResponse>> getTheaters() {
        return ResponseEntity.ok(ApiResponse.success(theaterService.getTheaters()));
    }
}
