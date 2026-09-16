package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.service.FavoriteTheaterService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "자주 찾는 극장", description = "자주 찾는 극장 API")
@RequiredArgsConstructor
@RequestMapping("/api/theaters/{theaterId}")
@RestController
public class FavoriteTheaterController {

    private final FavoriteTheaterService favoriteTheaterService;

    @Operation(summary = "자주 찾는 극장 추가", description = "해당 회원의 자주 찾는 극장을 추가합니다.")
    @PostMapping("/favorite")
    public ResponseEntity<ApiResponse<Void>> addFavoriteTheater(
            @PathVariable Long theaterId, @RequestHeader("X-Member-Id") Long memberId) {

        favoriteTheaterService.addFavoriteTheater(theaterId, memberId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
