package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.MovieLikeResponse;
import com.ceos24.cgv.domain.movie.service.MovieLikeService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// userId는 인증 도입 전까지 쿼리 파라미터로 받는다. DELETE 본문은 버려질 수 있어 세 엔드포인트를 맞췄다.
@Tag(name = "영화 찜", description = "영화 찜 등록/해제/목록")
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieLikeController {

    private final MovieLikeService movieLikeService;

    @Operation(summary = "영화 찜 (이미 찜한 경우에도 성공)")
    @PostMapping("/{movieId}/likes")
    public ApiResponse<Void> like(@PathVariable Long movieId,
                                  @Parameter(description = "인증 도입 전 임시") @RequestParam Long userId) {
        movieLikeService.like(movieId, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "영화 찜 해제 (찜하지 않은 경우에도 성공)")
    @DeleteMapping("/{movieId}/likes")
    public ApiResponse<Void> unlike(@PathVariable Long movieId,
                                    @Parameter(description = "인증 도입 전 임시") @RequestParam Long userId) {
        movieLikeService.unlike(movieId, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "내가 찜한 영화 목록 (최근 찜한 순)")
    @GetMapping("/likes")
    public ApiResponse<List<MovieLikeResponse>> myLikes(
            @Parameter(description = "인증 도입 전 임시") @RequestParam Long userId) {
        return ApiResponse.success(movieLikeService.findMyLikes(userId));
    }
}
