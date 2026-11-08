package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.movie.service.FavoriteMovieService;
import com.ceos24.cgv.global.common.ApiResponse;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "찜한 영화", description = "찜한 영화 관련 API")
@RestController
@RequiredArgsConstructor
public class FavoriteMovieController {

    private final FavoriteMovieService favoriteMovieService;

    @Operation(summary = "찜한 영화 등록", description = "사용자가 특정 영화를 찜한 영화로 등록합니다.")
    @PostMapping("/api/movies/{movieId}/favorite")
    public ResponseEntity<ApiResponse<Void>> addFavoriteMovie(
            @PathVariable Long movieId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        favoriteMovieService.addFavoriteMovie(movieId, userDetails.getMemberId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @Operation(summary = "찜한 영화 삭제", description = "등록된 찜한 영화를 삭제합니다.")
    @DeleteMapping("/api/favoriteMovies/{favoriteMovieId}")
    public ResponseEntity<ApiResponse<Void>> deleteFavoriteMovie(
            @PathVariable Long favoriteMovieId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        favoriteMovieService.deleteFavoriteMovie(favoriteMovieId, userDetails.getMemberId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "찜한 영화 목록 조회", description = "사용자가 찜한 영화 목록을 조회합니다.")
    @GetMapping("/api/favoriteMovies")
    public ResponseEntity<ApiResponse<GetMovieResponse>> getAllFavoriteMovies(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success(favoriteMovieService.getAllFavoriteMovies(userDetails.getMemberId())));
    }
}
