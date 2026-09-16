package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.service.FavoriteMovieService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "영화 찜", description = "영화 찜 관련 API")
@RequestMapping
@RequiredArgsConstructor
@RestController
public class FavoriteMovieController {

    private final FavoriteMovieService favoriteMovieService;

    @Operation(summary = "관심 영화 등록", description = "사용자가 특정 영화를 관심 영화로 등록합니다.")
    @PostMapping("/api/movies/{movieId}/favorite")
    public ResponseEntity<ApiResponse<Void>> addFavoriteMovie(
            @PathVariable Long movieId, @RequestHeader("X-Member-Id") Long memberId) {
        favoriteMovieService.addFavoriteMovie(movieId, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
