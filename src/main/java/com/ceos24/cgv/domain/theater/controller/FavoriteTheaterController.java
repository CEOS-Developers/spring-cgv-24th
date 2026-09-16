package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.service.FavoriteTheaterService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/theaters/{theaterId}")
@RestController
public class FavoriteTheaterController {

    private final FavoriteTheaterService favoriteTheaterService;

    @PostMapping("/favorite")
    public ResponseEntity<ApiResponse<Void>> addFavoriteTheater(
            @PathVariable Long theaterId, @RequestHeader("X-Member-Id") Long memberId) {

        favoriteTheaterService.addFavoriteTheater(theaterId, memberId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
