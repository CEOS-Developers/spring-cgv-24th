package com.ceos24.springboot.movie.controller;

import com.ceos24.springboot.movie.dto.MovieCreateRequest;
import com.ceos24.springboot.movie.dto.MovieResponse;
import com.ceos24.springboot.movie.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
@Tag(name = "Movie", description = "영화 생성 및 조회 API")
public class MovieController {

    private final MovieService movieService;

    // 영화 등록
    @Operation(summary = "영화 등록", description = "새로운 영화를 등록합니다.")
    @PostMapping
    public ResponseEntity<MovieResponse> createMovie(
            @RequestBody MovieCreateRequest request
    ) {
        MovieResponse response = movieService.createMovie(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 영화 전체 조회
    @Operation(summary = "영화 전체 조회", description = "등록된 모든 영화를 조회합니다.")
    @GetMapping
    public ResponseEntity<List<MovieResponse>> getMovies() {
        List<MovieResponse> movies = movieService.getMovies();

        return ResponseEntity.ok(movies);
    }

    // 영화 하나 조회
    @Operation(summary = "영화 하나 조회", description = "movieId를 이용해 특정 영화를 조회합니다.")
    @GetMapping("/{movieId}")
    public ResponseEntity<MovieResponse> getMovie(
            @PathVariable Long movieId
    ) {
        MovieResponse response = movieService.getMovie(movieId);

        return ResponseEntity.ok(response);
    }
}