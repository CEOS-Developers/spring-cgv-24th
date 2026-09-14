package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieResponse;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    /**
     * 영화 생성
     *
     * @param request
     * @return ResponseEntity<ApiResponse>
     * ├── HTTP 상태: 201
     * ├── 헤더
     * └── 본문: ApiResponse<Long>
     * ├── result: 영화 ID (Long)
     * ├── resultCode: 201
     * └── resultMsg: "INSERT_SUCCESS"
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createMovie(
            @Valid @RequestBody MovieCreateRequest request
    ) {
        Long movieId = movieService.createMovie(request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;

        ApiResponse<Long> response = new ApiResponse<>(
                movieId,
                code.getStatus(),
                code.getMessage()
        );

        /*
        ex:
          HTTP/1.1 201 Created
          Location: /api/movies/3

          { response의 내용 }
        */
        return ResponseEntity.created(URI.create("/api/movies/" + movieId)).body(response);
    }

    // 영화 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieResponse>>> getMovies() {
        List<MovieResponse> responses = movieService.getMovies();
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<MovieResponse>> body = new ApiResponse<>(
                responses,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 영화 조회
    @GetMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieResponse>> getMovie(
            @PathVariable("movieId") Long movieId
    ) {
        MovieResponse response = movieService.getMovie(movieId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<MovieResponse> body = new ApiResponse<>(
                response,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 영화 삭제
    @DeleteMapping("/{movieId}")
    public ResponseEntity<ApiResponse<Void>> deleteMovie(
            @PathVariable("movieId") Long movieId
    ) {
        movieService.deleteMovie(movieId);
        SuccessCode code = SuccessCode.DELETE_SUCCESS;

        ApiResponse<Void> body = new ApiResponse<>(
                null,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
