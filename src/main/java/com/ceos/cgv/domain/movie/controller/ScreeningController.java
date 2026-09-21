package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.dto.ScreeningCreateRequest;
import com.ceos.cgv.domain.movie.dto.ScreeningResponse;
import com.ceos.cgv.domain.movie.service.ScreeningService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "상영 일정", description = "영화와 상영관의 상영 일정 관리")
public class ScreeningController {
    private final ScreeningService screeningService;

    @PostMapping("/screenings")
    @Operation(summary = "상영 일정 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "상영 일정 생성 성공")
    public ResponseEntity<ApiResponse<ScreeningResponse>> create(@Valid @RequestBody ScreeningCreateRequest request) {
        ScreeningResponse response = ScreeningResponse.from(screeningService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/screenings/" + response.screeningId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping("/movies/{movieId}/screenings")
    @Operation(summary = "영화별 상영 일정 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상영 일정 목록 조회 성공")
    public ResponseEntity<ApiResponse<List<ScreeningResponse>>> findAllByMovieId(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(
                screeningService.findAllByMovieId(movieId), ScreeningResponse::from));
    }
}
