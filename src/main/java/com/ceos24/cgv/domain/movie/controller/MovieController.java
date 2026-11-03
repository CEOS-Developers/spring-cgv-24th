package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.response.MovieDetailResponse;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Movie", description = "영화 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "영화 목록 조회 API", description = "전체 영화 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<MovieListResponse>> getMovies() {
        return ApiResponse.onSuccess(movieService.findAll());
    }

    @Operation(summary = "영화 상세 조회 API", description = "특정 영화의 상세 정보를 조회합니다.")
    @GetMapping("/{movieId}")
    public ApiResponse<MovieDetailResponse> getMovie(@PathVariable Long movieId) {
        return ApiResponse.onSuccess(movieService.findById(movieId));
    }
}
