package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.service.MovieLikeService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies/{movieId}/likes")
@RequiredArgsConstructor
@Tag(name = "영화 찜", description = "영화 찜 및 취소")
public class MovieLikeController {
    private final MovieLikeService movieLikeService;

    @PostMapping
    @Operation(summary = "영화 찜 토글")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영화 찜 상태 변경 성공")
    public ResponseEntity<ApiResponse<Boolean>> toggle(
            @PathVariable Long movieId,
            @RequestParam Long userId
    ) {
        boolean liked = movieLikeService.toggle(userId, movieId);
        return ResponseEntity.ok(ApiResponse.success(liked));
    }
}
