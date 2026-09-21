package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.service.UserMovieService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
@Tag(
        name = "영화 찜 API",
        description = "영화 찜 등록 및 취소 API"
)
public class UserMovieController {

    private final UserMovieService userMovieService;

    // 1. 영화 찜 등록
    @PostMapping("/{movieId}/like")
    @Operation(
            summary = "영화 찜 등록",
            description = "사용자가 특정 영화를 찜합니다."
    )
    public ApiResponse<Void> likeMovie(
            @Parameter(description = "영화 ID")
            @PathVariable(name = "movieId")
            Long movieId,

            @Parameter(description = "사용자 ID")
            @RequestParam(name = "userId")
            Long userId
    ) {
        userMovieService.likeMovie(
                userId,
                movieId
        );

        return ApiResponse.onSuccess(
                "영화 찜 등록에 성공했습니다.",
                null
        );
    }

    // 2. 영화 찜 취소
    @DeleteMapping("/{movieId}/like")
    @Operation(
            summary = "영화 찜 취소",
            description = "사용자가 찜한 특정 영화를 찜 목록에서 삭제합니다."
    )
    public ApiResponse<Void> unlikeMovie(
            @Parameter(description = "영화 ID")
            @PathVariable(name = "movieId")
            Long movieId,

            @Parameter(description = "사용자 ID")
            @RequestParam(name = "userId")
            Long userId
    ) {
        userMovieService.unlikeMovie(
                userId,
                movieId
        );

        return ApiResponse.onSuccess(
                "영화 찜 취소에 성공했습니다.",
                null
        );
    }
}