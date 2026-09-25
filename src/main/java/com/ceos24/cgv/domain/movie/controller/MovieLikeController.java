package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.MovieLikeResponse;
import com.ceos24.cgv.domain.movie.service.MovieLikeService;
import com.ceos24.cgv.global.response.ApiResponse;
import com.ceos24.cgv.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "영화 찜", description = "영화 찜 등록/해제/목록")
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieLikeController {

    private final MovieLikeService movieLikeService;

    @Operation(summary = "영화 찜 (이미 찜한 경우에도 성공)")
    @PostMapping("/{movieId}/likes")
    public ApiResponse<Void> like(@AuthenticationPrincipal AuthUser authUser,
                                  @PathVariable Long movieId) {
        movieLikeService.like(movieId, authUser.userId());
        return ApiResponse.success();
    }

    @Operation(summary = "영화 찜 해제 (찜하지 않은 경우에도 성공)")
    @DeleteMapping("/{movieId}/likes")
    public ApiResponse<Void> unlike(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long movieId) {
        movieLikeService.unlike(movieId, authUser.userId());
        return ApiResponse.success();
    }

    @Operation(summary = "내가 찜한 영화 목록 (최근 찜한 순)")
    @GetMapping("/likes")
    public ApiResponse<List<MovieLikeResponse>> myLikes(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(movieLikeService.findMyLikes(authUser.userId()));
    }
}
