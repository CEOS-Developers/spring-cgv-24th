package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.request.MoviePersonCreateRequest;
import com.ceos24.cgv.domain.movie.service.MoviePersonService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MoviePerson", description = "영화 감독/배우 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies/{movieId}/persons")
public class MoviePersonController {

    private final MoviePersonService moviePersonService;

    @Operation(summary = "영화 인물 등록 API", description = "특정 영화에 감독 또는 배우를 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createMoviePerson(
            @PathVariable Long movieId,
            @Valid @RequestBody MoviePersonCreateRequest request
    ) {
        Long moviePersonId = moviePersonService.create(movieId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, moviePersonId));
    }
}
