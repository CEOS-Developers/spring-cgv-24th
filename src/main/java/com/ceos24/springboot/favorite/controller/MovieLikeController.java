package com.ceos24.springboot.favorite.controller;

import com.ceos24.springboot.favorite.dto.MovieLikeResponse;
import com.ceos24.springboot.favorite.service.MovieLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ceos24.springboot.user.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/likes/movies")
@Tag(name = "Movie Like", description = "영화 찜 API")
public class MovieLikeController {

    private final MovieLikeService movieLikeService;


    // 영화 찜 등록
    @Operation(summary = "영화 찜 등록")
    @PostMapping("/{movieId}")
    public ResponseEntity<MovieLikeResponse> addMovieLike(
            @PathVariable Long movieId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        MovieLikeResponse response =
                movieLikeService.addMovieLike(
                        userDetails.getUserId(),
                        movieId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // 찜한 영화 목록
    @Operation(summary = "찜한 영화 목록 조회")
    @GetMapping
    public ResponseEntity<List<MovieLikeResponse>> getMovieLikes(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        return ResponseEntity.ok(
                movieLikeService.getMovieLikes(
                        userDetails.getUserId()
                )
        );
    }


    // 영화 찜 해제
    @Operation(summary = "영화 찜 해제")
    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteMovieLike(
            @PathVariable Long movieId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        movieLikeService.deleteMovieLike(
                userDetails.getUserId(),
                movieId
        );

        return ResponseEntity.noContent().build();
    }
}