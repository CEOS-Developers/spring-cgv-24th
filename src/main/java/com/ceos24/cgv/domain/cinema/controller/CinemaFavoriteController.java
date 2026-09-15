package com.ceos24.cgv.domain.cinema.controller;

import com.ceos24.cgv.domain.cinema.dto.response.CinemaFavoriteResponse;
import com.ceos24.cgv.domain.cinema.service.CinemaFavoriteService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/favorite-cinemas")
public class CinemaFavoriteController {

    private final CinemaFavoriteService cinemaFavoriteService;

    @PostMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<Long>> addFavorite(
            @PathVariable("userId") Long userId,
            @PathVariable("cinemaId") Long cinemaId
    ) {
        Long favoriteId = cinemaFavoriteService.createCinemaFavorite(userId, cinemaId);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;
        ApiResponse<Long> body = new ApiResponse<>(
                favoriteId,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @DeleteMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<Void>> deleteFavorite(
            @PathVariable("userId") Long userId,
            @PathVariable("cinemaId") Long cinemaId
    ) {
        cinemaFavoriteService.removeCinemaFavorite(userId, cinemaId);
        SuccessCode code = SuccessCode.DELETE_SUCCESS;
        ApiResponse<Void> body = new ApiResponse<>(
                null,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaFavoriteResponse>>> getFavorites(
            @PathVariable("userId") Long userId
    ) {
        List<CinemaFavoriteResponse> favorites =
                cinemaFavoriteService.getCinemaFavorites(userId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        ApiResponse<List<CinemaFavoriteResponse>> body = new ApiResponse<>(
                favorites,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
