package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.response.MovieLikeResponse;
import com.ceos24.cgv.domain.movie.service.MovieLikeService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import com.ceos24.cgv.global.config.SwaggerConfig;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MovieLike", description = "영화 찜 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies/{movieId}/likes")
public class MovieLikeController {

    private final MovieLikeService movieLikeService;

    @Operation(summary = "영화 찜하기 API", description = "특정 영화를 찜합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @PostMapping
    public ResponseEntity<ApiResponse<MovieLikeResponse>> likeMovie(
            @PathVariable Long movieId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MovieLikeResponse response = movieLikeService.like(userDetails.getUserId(), movieId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, response));
    }

    @Operation(summary = "영화 찜 취소 API", description = "찜한 영화를 취소합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @DeleteMapping
    public ApiResponse<Void> unlikeMovie(
            @PathVariable Long movieId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        movieLikeService.unlike(userDetails.getUserId(), movieId);
        return ApiResponse.onSuccess(null);
    }
}
