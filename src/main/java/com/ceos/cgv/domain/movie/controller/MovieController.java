package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.dto.MovieCreateRequest;
import com.ceos.cgv.domain.movie.dto.MovieResponse;
import com.ceos.cgv.domain.movie.service.MovieService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "영화", description = "영화 생성·조회·삭제")
public class MovieController {
    private final MovieService movieService;

    @PostMapping
    @Operation(summary = "영화 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "영화 생성 성공")
    public ResponseEntity<ApiResponse<MovieResponse>> create(@Valid @RequestBody MovieCreateRequest request) {
        MovieResponse response = MovieResponse.from(movieService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/movies/" + response.movieId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping
    @Operation(summary = "영화 전체 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영화 목록 조회 성공")
    public ResponseEntity<ApiResponse<List<MovieResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(movieService.findAll(), MovieResponse::from));
    }

    @GetMapping("/{movieId}")
    @Operation(summary = "영화 상세 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영화 상세 조회 성공")
    public ResponseEntity<ApiResponse<MovieResponse>> findById(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(MovieResponse.from(movieService.findById(movieId))));
    }

    @DeleteMapping("/{movieId}")
    @Operation(summary = "영화 삭제")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "영화 삭제 성공")
    public ResponseEntity<Void> delete(@PathVariable Long movieId) {
        movieService.delete(movieId);
        return ResponseEntity.noContent().build();
    }
}
