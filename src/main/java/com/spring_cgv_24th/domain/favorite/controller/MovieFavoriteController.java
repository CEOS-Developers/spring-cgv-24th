package com.spring_cgv_24th.domain.favorite.controller;

import com.spring_cgv_24th.domain.favorite.dto.MovieFavoriteResDTO;
import com.spring_cgv_24th.domain.favorite.service.MovieFavoriteService;
import com.spring_cgv_24th.domain.movie.dto.MovieResDTO;
import com.spring_cgv_24th.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MovieFavorite", description = "영화 찜 API")
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
            @Parameter(description = "임시로 사용하는 값")
            @Positive @RequestParam("memberId") Long memberId) {
        return ApiResponse.onCreated(movieFavoriteService.addFavorite(movieId, memberId));
    }

    @Operation(summary = "영화 찜 해제")
    @DeleteMapping("/{movieId}/favorites")
    public ApiResponse<Void> removeFavorite(
            @Positive @PathVariable("movieId") Long movieId,
            @Parameter(description = "임시로 사용하는 값")
            @Positive @RequestParam("memberId") Long memberId) {
        movieFavoriteService.removeFavorite(movieId, memberId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "영화 찜 목록 조회")
    @GetMapping("/favorites")
    public ApiResponse<List<MovieResDTO>> getFavorites(
            @Parameter(description = "임시: 로그인 구현 후 요청에서 제거하고 인증된 회원 ID를 사용합니다.")
            @Positive @RequestParam("memberId") Long memberId) {
        return ApiResponse.onSuccess(movieFavoriteService.getFavorites(memberId));
    }
}
