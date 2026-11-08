package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieImageCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MoviePersonCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieStatisticsRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieUpdateRequest;
import com.ceos24.cgv.domain.movie.service.MovieImageService;
import com.ceos24.cgv.domain.movie.service.MoviePersonService;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.domain.movie.service.MovieStatisticsService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import com.ceos24.cgv.global.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Movie (관리자)", description = "영화 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/movies")
@SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
public class MovieAdminController {

    private final MovieService movieService;
    private final MovieImageService movieImageService;
    private final MoviePersonService moviePersonService;
    private final MovieStatisticsService movieStatisticsService;

    @Operation(summary = "영화 생성 API", description = "새로운 영화 데이터를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createMovie(@Valid @RequestBody MovieCreateRequest request) {
        Long movieId = movieService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, movieId));
    }

    @Operation(summary = "영화 수정 API", description = "특정 영화 정보를 수정합니다.")
    @PatchMapping("/{movieId}")
    public ApiResponse<Void> updateMovie(
            @PathVariable Long movieId,
            @Valid @RequestBody MovieUpdateRequest request
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

    @Operation(summary = "영화 이미지 등록 API", description = "특정 영화에 포스터 또는 스틸컷 이미지를 등록합니다.")
    @PostMapping("/{movieId}/images")
    public ResponseEntity<ApiResponse<Long>> createMovieImage(
            @PathVariable Long movieId,
            @Valid @RequestBody MovieImageCreateRequest request
    ) {
        Long imageId = movieImageService.create(movieId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, imageId));
    }

    @Operation(summary = "영화 인물 등록 API", description = "특정 영화에 감독 또는 배우를 등록합니다.")
    @PostMapping("/{movieId}/persons")
    public ResponseEntity<ApiResponse<Long>> createMoviePerson(
            @PathVariable Long movieId,
            @Valid @RequestBody MoviePersonCreateRequest request
    ) {
        Long moviePersonId = moviePersonService.create(movieId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, moviePersonId));
    }

    @Operation(summary = "영화 통계 등록/수정 API", description = "특정 영화의 통계 정보(예매율, 누적관객수 등)를 등록하거나 갱신합니다.")
    @PatchMapping("/{movieId}/statistics")
    public ApiResponse<Long> createOrUpdateStatistics(
            @PathVariable Long movieId,
            @Valid @RequestBody MovieStatisticsRequest request
    ) {
        Long statisticsId = movieStatisticsService.createOrUpdate(movieId, request);
        return ApiResponse.onSuccess(statisticsId);
    }
}
