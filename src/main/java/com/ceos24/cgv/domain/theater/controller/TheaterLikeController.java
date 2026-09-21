package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.TheaterLikeResponse;
import com.ceos24.cgv.domain.theater.service.TheaterLikeService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "TheaterLike", description = "영화관 찜 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters/{theaterId}/likes")
public class TheaterLikeController {

    private final TheaterLikeService theaterLikeService;

    @Operation(summary = "영화관 찜하기 API", description = "특정 영화관을 찜합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<TheaterLikeResponse>> likeTheater(
            @PathVariable Long theaterId,
            @RequestParam Long userId
    ) {
        TheaterLikeResponse response = theaterLikeService.like(userId, theaterId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, response));
    }

    @Operation(summary = "영화관 찜 취소 API", description = "찜한 영화관을 취소합니다.")
    @DeleteMapping
    public ApiResponse<Void> unlikeTheater(
            @PathVariable Long theaterId,
            @RequestParam Long userId
    ) {
        theaterLikeService.unlike(userId, theaterId);
        return ApiResponse.onSuccess(null);
    }
}
