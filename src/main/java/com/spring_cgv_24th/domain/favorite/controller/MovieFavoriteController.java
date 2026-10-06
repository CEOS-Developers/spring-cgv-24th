package com.spring_cgv_24th.domain.favorite.controller;

import com.spring_cgv_24th.domain.favorite.dto.MovieFavoriteResDTO;
import com.spring_cgv_24th.domain.favorite.service.MovieFavoriteService;
import com.spring_cgv_24th.domain.movie.dto.MovieResDTO;
import com.spring_cgv_24th.global.response.ApiResponse;
import com.spring_cgv_24th.global.security.principal.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MovieFavorite", description = "영화 찜 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
public class MovieFavoriteController {

    private final MovieFavoriteService movieFavoriteService;

    @Operation(summary = "영화 찜 추가")
    @PostMapping("/{movieId}/favorites")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MovieFavoriteResDTO> addFavorite(
            @Positive @PathVariable("movieId") Long movieId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.onCreated(movieFavoriteService.addFavorite(movieId, principal.getMemberId()));
    }

    @Operation(summary = "영화 찜 해제")
    @DeleteMapping("/{movieId}/favorites")
    public ApiResponse<Void> removeFavorite(
            @Positive @PathVariable("movieId") Long movieId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        movieFavoriteService.removeFavorite(movieId, principal.getMemberId());
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "영화 찜 목록 조회")
    @GetMapping("/favorites")
    public ApiResponse<List<MovieResDTO>> getFavorites(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.onSuccess(movieFavoriteService.getFavorites(principal.getMemberId()));
    }
}
