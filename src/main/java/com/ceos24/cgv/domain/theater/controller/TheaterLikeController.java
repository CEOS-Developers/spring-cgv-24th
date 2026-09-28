package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.TheaterLikeResponse;
import com.ceos24.cgv.domain.theater.service.TheaterLikeService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "TheaterLike", description = "영화관 찜 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters/{theaterId}/likes")
public class TheaterLikeController {

    private final TheaterLikeService theaterLikeService;

    @Operation(summary = "영화관 찜하기 API", description = "로그인한 사용자가 특정 영화관을 찜합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @PostMapping
    public ResponseEntity<ApiResponse<TheaterLikeResponse>> likeTheater(
            @PathVariable Long theaterId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        TheaterLikeResponse response = theaterLikeService.like(userDetails.getUserId(), theaterId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, response));
    }

    @Operation(summary = "영화관 찜 취소 API", description = "로그인한 사용자가 찜한 영화관을 취소합니다.")
    @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    @DeleteMapping
    public ApiResponse<Void> unlikeTheater(
            @PathVariable Long theaterId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        theaterLikeService.unlike(userDetails.getUserId(), theaterId);
        return ApiResponse.onSuccess(null);
    }
}
