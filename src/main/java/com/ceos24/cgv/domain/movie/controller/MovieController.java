package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.MovieResponse;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "영화", description = "영화 조회")
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "영화 목록 조회")
    @GetMapping
    public ApiResponse<List<MovieResponse>> list() {
        return ApiResponse.success(movieService.findAll());
    }

    @Operation(summary = "영화 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<MovieResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(movieService.findById(id));
    }
}
