package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.request.MovieStatisticsRequest;
import com.ceos24.cgv.domain.movie.service.MovieStatisticsService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MovieStatistics", description = "영화 통계 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies/{movieId}/statistics")
public class MovieStatisticsController {

    private final MovieStatisticsService movieStatisticsService;

    @Operation(summary = "영화 통계 등록/수정 API", description = "특정 영화의 통계 정보(예매율, 누적관객수 등)를 등록하거나 갱신합니다.")
    @PatchMapping
    public ApiResponse<Long> createOrUpdateStatistics(
            @PathVariable Long movieId,
            @Valid @RequestBody MovieStatisticsRequest request
    ) {
        Long statisticsId = movieStatisticsService.createOrUpdate(movieId, request);
        return ApiResponse.onSuccess(statisticsId);
    }
}
