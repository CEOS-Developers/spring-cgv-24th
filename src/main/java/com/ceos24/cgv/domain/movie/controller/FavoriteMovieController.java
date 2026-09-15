package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.repository.FavoriteMovieRepository;
import com.ceos24.cgv.domain.movie.service.FavoriteMovieService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping
@RequiredArgsConstructor
@RestController
public class FavoriteMovieController {

    private final FavoriteMovieService favoriteMovieService;

    @PostMapping("/api/movies/{movieId}/favorite")
    public ResponseEntity<ApiResponse<Void>> addFavoriteMovie(
            @PathVariable Long movieId,
            @RequestHeader("X-Member-Id") Long memberId) {
        favoriteMovieService.addFavoriteMovie(movieId, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

}
