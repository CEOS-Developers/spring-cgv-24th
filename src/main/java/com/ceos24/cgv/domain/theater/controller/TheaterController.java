package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetTheaterResponse;
import com.ceos24.cgv.domain.theater.service.TheaterService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "영화관", description = "영화관 관련 API")
@RequestMapping("/api/theaters")
@RequiredArgsConstructor
@RestController
public class TheaterController {

    private final TheaterService theaterService;

    @Operation(summary = "영화관 목록 조회", description = "전체 영화관 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<GetTheaterResponse>> getTheaters() {
        return ResponseEntity.ok(ApiResponse.success(theaterService.getTheaters()));
    }
}
