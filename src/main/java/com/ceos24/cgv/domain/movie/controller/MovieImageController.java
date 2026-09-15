package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.request.MovieImageCreateRequest;
import com.ceos24.cgv.domain.movie.service.MovieImageService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MovieImage", description = "영화 이미지 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies/{movieId}/images")
public class MovieImageController {

    private final MovieImageService movieImageService;

    @Operation(summary = "영화 이미지 등록 API", description = "특정 영화에 포스터 또는 스틸컷 이미지를 등록합니다.")
    @PostMapping
    public ApiResponse<Long> createMovieImage(
            @PathVariable Long movieId,
            @Valid @RequestBody MovieImageCreateRequest request
    ) {
        Long imageId = movieImageService.create(movieId, request);
        return ApiResponse.onSuccess(SuccessStatus.CREATED, imageId);
    }
}
