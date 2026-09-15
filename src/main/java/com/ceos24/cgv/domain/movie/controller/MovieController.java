package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieUpdateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieDetailResponse;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Movie", description = "영화 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "영화 생성 API", description = "새로운 영화 데이터를 생성합니다.")
    @PostMapping
    public ApiResponse<Long> createMovie(@Valid @RequestBody MovieCreateRequest request) {
        Long movieId = movieService.create(request);
        return ApiResponse.onSuccess(SuccessStatus.CREATED, movieId);
    }

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

    @Operation(summary = "영화 수정 API", description = "특정 영화 정보를 수정합니다.")
    @PatchMapping("/{movieId}")
    public ApiResponse<Void> updateMovie(
            @PathVariable Long movieId,
            @RequestBody MovieUpdateRequest request
    ) {
        movieService.update(movieId, request);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "영화 삭제 API", description = "특정 영화를 삭제합니다.")
    @DeleteMapping("/{movieId}")
    public ApiResponse<Void> deleteMovie(@PathVariable Long movieId) {
        movieService.delete(movieId);
        return ApiResponse.onSuccess(null);
    }
}
