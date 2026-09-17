package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetTheaterResponse;
import com.ceos24.cgv.domain.theater.service.FavoriteTheaterService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "찜한 극장", description = "찜한 극장 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FavoriteTheaterController {

    private final FavoriteTheaterService favoriteTheaterService;

    @Operation(summary = "찜한 극장 추가", description = "해당 회원의 찜한 극장을 추가합니다.")
    @PostMapping("/theaters/{theaterId}/favorite")
    public ResponseEntity<ApiResponse<Void>> addFavoriteTheater(
            @PathVariable Long theaterId,
            @RequestHeader("X-Member-Id") Long memberId) {
        favoriteTheaterService.addFavoriteTheater(theaterId, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @Operation(summary = "찜한 극장 삭제", description = "등록된 찜한 극장을 삭제합니다.")
    @DeleteMapping("/favoriteTheaters/{favoriteTheaterId}")
    public ResponseEntity<ApiResponse<Void>> deleteFavoriteTheater(
            @PathVariable Long favoriteTheaterId,
            @RequestHeader("X-Member-Id") Long memberId) {
        favoriteTheaterService.deleteFavoriteTheater(favoriteTheaterId, memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "찜한 극장 목록 조회", description = "사용자가 찜한 극장 목록을 조회합니다.")
    @GetMapping("/favoriteTheaters")
    public ResponseEntity<ApiResponse<GetTheaterResponse>> getAllFavoriteTheaters(
            @RequestHeader("X-Member-Id") Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(favoriteTheaterService.getAllFavoriteTheaters(memberId)));
    }
}
