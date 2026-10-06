package com.ceos24.spring_cgv.domain.like.controller;

import com.ceos24.spring_cgv.domain.like.dto.response.CinemaLikeResponse;
import com.ceos24.spring_cgv.domain.like.exception.code.CinemaLikeSuccessCode;
import com.ceos24.spring_cgv.domain.like.service.CinemaLikeService;
import com.ceos24.spring_cgv.global.apipayload.ApiResponse;
import com.ceos24.spring_cgv.global.security.userdetails.CustomUserDetails;
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

@Tag(name = "영화관 찜", description = "영화관 찜 관련 API")
@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaLikeController {

    private final CinemaLikeService cinemaLikeService;

    @Operation(summary = "영화관 찜 등록", description = "해당 영화관을 내 찜 목록에 추가합니다.")
    @PostMapping("/{cinemaId}/likes")
    public ApiResponse<CinemaLikeResponse> createCinemaLike(
            @PathVariable Long cinemaId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        return ApiResponse.onSuccess(
                CinemaLikeSuccessCode.CINEMA_LIKE_CREATED,
                cinemaLikeService.createCinemaLike(userDetails.getMemberId(), cinemaId));
    }

    @Operation(summary = "영화관 찜 취소", description = "해당 영화관을 내 찜 목록에서 제거합니다.")
    @DeleteMapping("/{cinemaId}/likes")
    public ApiResponse<Void> deleteCinemaLike(
            @PathVariable Long cinemaId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        cinemaLikeService.deleteCinemaLike(userDetails.getMemberId(), cinemaId);

        return ApiResponse.onSuccess(CinemaLikeSuccessCode.CINEMA_LIKE_DELETED);
    }

    @Operation(summary = "내 찜한 영화관 목록 조회", description = "내가 찜한 영화관을 최근에 찜한 순서로 조회합니다.")
    @GetMapping("/likes/me")
    public ApiResponse<List<CinemaLikeResponse>> findMyCinemaLikes(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        return ApiResponse.onSuccess(
                CinemaLikeSuccessCode.CINEMA_LIKE_LIST_FETCHED,
                cinemaLikeService.findMyCinemaLikes(userDetails.getMemberId()));
    }
}