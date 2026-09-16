package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.dto.MovieCreateRequest;
import com.ceos.cgv.domain.movie.dto.MovieResponse;
import com.ceos.cgv.domain.movie.service.MovieService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {
    private final MovieService movieService;

    @PostMapping
    public ResponseEntity<ApiResponse<MovieResponse>> create(@Valid @RequestBody MovieCreateRequest request) {
        MovieResponse response = MovieResponse.from(movieService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/movies/" + response.movieId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(movieService.findAll(), MovieResponse::from));
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieResponse>> findById(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(MovieResponse.from(movieService.findById(movieId))));
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> delete(@PathVariable Long movieId) {
        movieService.delete(movieId);
        return ResponseEntity.noContent().build();
    }
}
