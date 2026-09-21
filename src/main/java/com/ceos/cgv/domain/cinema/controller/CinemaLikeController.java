package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.service.CinemaLikeService;
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
@RequestMapping("/api/v1/cinemas/{cinemaId}/likes")
@RequiredArgsConstructor
@Tag(name = "영화관 찜", description = "영화관 찜 및 취소")
public class CinemaLikeController {
    private final CinemaLikeService cinemaLikeService;

    @PostMapping
    @Operation(summary = "영화관 찜 토글")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영화관 찜 상태 변경 성공")
    public ResponseEntity<ApiResponse<Boolean>> toggle(
            @PathVariable Long cinemaId,
            @RequestParam Long userId
    ) {
        boolean liked = cinemaLikeService.toggle(userId, cinemaId);
        return ResponseEntity.ok(ApiResponse.success(liked));
    }
}
