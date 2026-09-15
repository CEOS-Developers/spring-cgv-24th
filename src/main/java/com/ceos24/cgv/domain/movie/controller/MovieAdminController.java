package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.request.CreateMovieRequest;
import com.ceos24.cgv.domain.movie.service.MovieAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class MovieAdminController {
    private final MovieAdminService movieAdminService;

    @PostMapping("/movies")
    public ResponseEntity<ApiResponse<Void>> createMovie(
            @RequestBody CreateMovieRequest request
    ) {
        movieAdminService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
