package com.ceos24.cgv.domain.movie.controller;


import com.ceos24.cgv.domain.movie.dto.request.MovieRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieDetailResponse;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
@Tag(name = "영화 API")
public class MovieController {

    private final MovieService movieService;

    //1. 영화 생성

    @Operation(
            summary = "영화 생성",
            description = "영화를 등록하고 기본 영화 통계를 함께 생성합니다."
    )
    @PostMapping
    public ApiResponse<MovieDetailResponse> createMovie(
            @Valid @RequestBody MovieRequest request
    ) {
        MovieDetailResponse response = movieService.createMovie(request);

        return ApiResponse.onSuccess("영화 생성에 성공했습니다.", response);
    }

    //2. 영화 전체 조회
    @Operation(
            summary = "영화 전체 조회",
            description = "전체 영화 목록을 개봉일 내림차순으로 조회합니다."
    )
    @GetMapping
    public ApiResponse<List<MovieListResponse>> getMovies() {
        List<MovieListResponse> response = movieService.getMovies();

        return ApiResponse.onSuccess("영화 목록 조회에 성공했습니다.", response);
    }


    //3.영화 단건 조회
    @Operation(
            summary = "영화 단건 조회",
            description = "영화 ID로 영화 상세 정보와 영화 통계를 함께 조회합니다."
    )
    @GetMapping("/{movieId}")
    public ApiResponse<MovieDetailResponse> getMovie(
            @PathVariable("movieId") Long movieId
    ) {
        MovieDetailResponse response = movieService.getMovie(movieId);

        return ApiResponse.onSuccess("영화 상세 조회에 성공했습니다.", response);
    }

    //4.영화 삭제
    @Operation(
            summary = "영화 삭제",
            description = "영화 ID에 해당하는 영화와 연결된 영화 통계를 삭제합니다."
    )
    @DeleteMapping("/{movieId}")
    public ApiResponse<Void> deleteMovie(
            @PathVariable("movieId") Long movieId
    ) {
        movieService.deleteMovie(movieId);

        return ApiResponse.onSuccess("영화 삭제에 성공했습니다.");
    }
}
