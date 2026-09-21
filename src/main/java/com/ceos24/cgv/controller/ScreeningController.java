package com.ceos24.cgv.controller;

import com.ceos24.cgv.dto.response.ScreeningResponse;
import com.ceos24.cgv.dto.response.ScreeningSeatsResponse;
import com.ceos24.cgv.service.ScreeningService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "회차 목록 조회 (영화/지점/날짜 필터)")
    @GetMapping
    public List<ScreeningResponse> list(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return screeningService.search(movieId, branchId, date);
    }

    @Operation(summary = "회차 좌석 조회 (상영관 크기 + 예매된 좌석)")
    @GetMapping("/{id}/seats")
    public ScreeningSeatsResponse seats(@PathVariable Long id) {
        return screeningService.getSeats(id);
    }
}
