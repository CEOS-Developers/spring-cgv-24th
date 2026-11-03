package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.TheaterResponse;
import com.ceos24.cgv.domain.theater.service.TheaterService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Theater", description = "영화관 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
public class TheaterController {

    private final TheaterService theaterService;

    @Operation(summary = "영화관 목록 조회 API", description = "전체 영화관 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<TheaterResponse>> getTheaters() {
        return ApiResponse.onSuccess(theaterService.findAll());
    }

    @Operation(summary = "영화관 상세 조회 API", description = "특정 영화관의 상세 정보를 조회합니다.")
    @GetMapping("/{theaterId}")
    public ApiResponse<TheaterResponse> getTheater(@PathVariable Long theaterId) {
        return ApiResponse.onSuccess(theaterService.findById(theaterId));
    }
}
