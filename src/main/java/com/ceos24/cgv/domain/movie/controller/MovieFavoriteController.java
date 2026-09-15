package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.response.MovieFavoriteResponse;
import com.ceos24.cgv.domain.movie.service.MovieFavoriteService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/favorite-movies")
public class MovieFavoriteController {

    private final MovieFavoriteService movieFavoriteService;

    @PostMapping("/{movieId}")
    public ResponseEntity<ApiResponse<Long>> addFavorite(
            @PathVariable("userId") Long userId,
            @PathVariable("movieId") Long movieId
    ) {
        Long movieFavoriteId = movieFavoriteService.createMovieFavorite(userId, movieId);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;

        ApiResponse<Long> body = new ApiResponse<>(
                movieFavoriteId,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<ApiResponse<Void>> deleteFavorite(
            @PathVariable("userId") Long userId,
            @PathVariable("movieId") Long movieId
    ) {
        movieFavoriteService.removeMovieFavorite(userId, movieId);
        SuccessCode code = SuccessCode.DELETE_SUCCESS;

        ApiResponse<Void> body = new ApiResponse<>(
                null,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieFavoriteResponse>>> getFavorites(
            @PathVariable("userId") Long userId
    ) {
        List<MovieFavoriteResponse> movieFavorites = movieFavoriteService.getMovieFavorites(userId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<MovieFavoriteResponse>> body = new ApiResponse<>(
                movieFavorites,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
