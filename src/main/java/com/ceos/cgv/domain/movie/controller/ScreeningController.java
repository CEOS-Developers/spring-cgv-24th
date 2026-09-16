package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.dto.ScreeningCreateRequest;
import com.ceos.cgv.domain.movie.dto.ScreeningResponse;
import com.ceos.cgv.domain.movie.service.ScreeningService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScreeningController {
    private final ScreeningService screeningService;

    @PostMapping("/screenings")
    public ResponseEntity<ApiResponse<ScreeningResponse>> create(@Valid @RequestBody ScreeningCreateRequest request) {
        ScreeningResponse response = ScreeningResponse.from(screeningService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/screenings/" + response.screeningId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping("/movies/{movieId}/screenings")
    public ResponseEntity<ApiResponse<List<ScreeningResponse>>> findAllByMovieId(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(
                screeningService.findAllByMovieId(movieId), ScreeningResponse::from));
    }
}
