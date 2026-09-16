package com.ceos24.cgv.domain.cinema.controller;

import com.ceos24.cgv.domain.cinema.dto.response.CinemaFavoriteResponse;
import com.ceos24.cgv.domain.cinema.service.CinemaFavoriteService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "영화관 찜",
        description = "사용자의 영화관 찜 등록, 목록 조회 및 삭제 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/favorite-cinemas")
public class CinemaFavoriteController {

    private final CinemaFavoriteService cinemaFavoriteService;

    @Operation(
            summary = "영화관 찜 등록",
            description = "사용자가 선택한 영화관을 찜 목록에 등록합니다."
    )
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

    @Operation(
            summary = "영화관 찜 삭제",
            description = "사용자의 영화관 찜 목록에서 선택한 영화관을 삭제합니다."
    )
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

    @Operation(
            summary = "영화관 찜 목록 조회",
            description = "사용자가 찜한 모든 영화관 목록을 조회합니다."
    )
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
