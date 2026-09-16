package com.cgvclone.cgv.domain.movie;

import static org.springframework.http.HttpStatus.OK;

import com.cgvclone.cgv.domain.movie.dto.MovieDetailResponse;
import com.cgvclone.cgv.domain.movie.dto.MovieListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @GetMapping
    public ResponseEntity<MovieListResponse> getMovies() {
        MovieListResponse movieListResponse = movieService.getMovies();
        return ResponseEntity
                .status(OK)
                .body(movieListResponse);
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<MovieDetailResponse> getMovie(@PathVariable Long movieId) {
        MovieDetailResponse movieDetailResponse = movieService.getMovie(movieId);
        return ResponseEntity
                .status(OK)
                .body(movieDetailResponse);
    }
}
