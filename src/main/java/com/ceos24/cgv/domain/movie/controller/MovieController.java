package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.movie.dto.response.GetScreeningResponse;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RequiredArgsConstructor
@RestController
public class MovieController {

    private final MovieService movieService;

    @GetMapping("/movies")
    public ResponseEntity<ApiResponse<GetMovieResponse>> getAllMovies(){
        return ResponseEntity.ok(ApiResponse.success(movieService.getAllMovies()));
    }

    @GetMapping("/theaters/{theaterId}/movies")
    public ResponseEntity<ApiResponse<GetMovieResponse>> getMovies(@PathVariable Long theaterId){
        return ResponseEntity.ok(ApiResponse.success(movieService.getMovies(theaterId)));
    }

    @GetMapping("/theaters/{theaterId}/screenings")
    public ResponseEntity<ApiResponse<GetScreeningResponse>> getScreenings(@PathVariable Long theaterId){
        return ResponseEntity.ok(ApiResponse.success(movieService.getScreenings(theaterId)));
    }
}
