package com.ceos24.cgv.controller;

import com.ceos24.cgv.dto.response.MovieResponse;
import com.ceos24.cgv.service.MovieService;
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
    public List<MovieResponse> list() {
        return movieService.findAll();
    }

    @Operation(summary = "영화 단건 조회")
    @GetMapping("/{id}")
    public MovieResponse detail(@PathVariable Long id) {
        return movieService.findById(id);
    }
}
