package com.ceos24.cgv.domain.screening.controller;

import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.screening.dto.ScreeningListResponse;
import com.ceos24.cgv.domain.screening.dto.ScreeningSeatsResponse;
import com.ceos24.cgv.domain.screening.entity.TimeSlot;
import com.ceos24.cgv.domain.screening.service.ScreeningService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "회차", description = "회차(상영) 조회")
@RestController
@RequestMapping("/api/screenings")
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;

    @Operation(summary = "회차 목록 조회 (영화·극장·날짜·상영관 종류·시간대 필터)")
    @GetMapping
    public ApiResponse<ScreeningListResponse> list(
            @RequestParam(required = false) Long movieId,
            @Parameter(description = "극장 복수 선택. 예) branchIds=1,2,3")
            @RequestParam(required = false) List<Long> branchIds,
            @Parameter(description = "생략하면 오늘")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "상영관 종류 탭. 생략하면 전체")
            @RequestParam(required = false) TheaterType theaterType,
            @Parameter(description = "시간대 탭. 생략하면 전체")
            @RequestParam(required = false) TimeSlot timeSlot) {
        return ApiResponse.success(
                screeningService.search(movieId, branchIds, date, theaterType, timeSlot));
    }

    @Operation(summary = "회차 좌석 조회 (상영관 크기 + 막힌 좌석)")
    @GetMapping("/{id}/seats")
    public ApiResponse<ScreeningSeatsResponse> seats(@PathVariable Long id) {
        return ApiResponse.success(screeningService.getSeats(id));
    }
}
