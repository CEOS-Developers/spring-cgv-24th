package com.spring_cgv_24th.domain.screening.controller;

import com.spring_cgv_24th.domain.screening.dto.request.ScreeningReqDTO;
import com.spring_cgv_24th.domain.screening.dto.response.ScreeningResDTO;
import com.spring_cgv_24th.domain.screening.dto.response.ScreeningSeatResDTO;
import com.spring_cgv_24th.domain.screening.service.ScreeningService;
import com.spring_cgv_24th.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Screening", description = "상영 회차 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/screenings")
public class ScreeningController {

    private final ScreeningService screeningService;

    @Operation(summary = "상영 회차 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScreeningResDTO> createScreening(
            @Valid @RequestBody ScreeningReqDTO.CreateScreeningDTO request) {
        return ApiResponse.onCreated(screeningService.createScreening(request));
    }

    @Operation(summary = "상영 회차 목록 조회")
    @GetMapping
    public ApiResponse<List<ScreeningResDTO>> getScreenings() {
        return ApiResponse.onSuccess(screeningService.getScreenings());
    }

    @Operation(summary = "상영 회차 상세 조회")
    @GetMapping("/{screeningId}")
    public ApiResponse<ScreeningResDTO> getScreening(@PathVariable("screeningId") Long screeningId) {
        return ApiResponse.onSuccess(screeningService.getScreening(screeningId));
    }

    @Operation(summary = "회차별 좌석 조회")
    @GetMapping("/{screeningId}/seats")
    public ApiResponse<List<ScreeningSeatResDTO>> getSeats(@PathVariable("screeningId") Long screeningId) {
        return ApiResponse.onSuccess(screeningService.getSeats(screeningId));
    }
}
