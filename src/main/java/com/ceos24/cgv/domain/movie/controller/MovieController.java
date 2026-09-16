package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.movie.dto.response.GetScreeningResponse;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "영화", description = "영화 관련 API")
@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "전체 영화 목록 조회", description = "모든 영화의 목록을 조회합니다.")
    @GetMapping("/movies")
    public ResponseEntity<ApiResponse<GetMovieResponse>> getAllMovies() {
        return ResponseEntity.ok(ApiResponse.success(movieService.getAllMovies()));
    }

    @Operation(summary = "극장별 영화 조회", description = "특정 극장에서 상영하는 영화 목록을 조회합니다.")
    @GetMapping("/theaters/{theaterId}/movies")
    public ResponseEntity<ApiResponse<GetMovieResponse>> getMovies(@PathVariable Long theaterId) {
        return ResponseEntity.ok(ApiResponse.success(movieService.getMovies(theaterId)));
    }

    @Operation(summary = "극장별 상영 시간표 조회", description = "특정 극장의 상영 시간표를 조회합니다.")
    @GetMapping("/theaters/{theaterId}/screenings")
    public ResponseEntity<ApiResponse<GetScreeningResponse>> getScreenings(@PathVariable Long theaterId) {
        return ResponseEntity.ok(ApiResponse.success(movieService.getScreenings(theaterId)));
    }
}
