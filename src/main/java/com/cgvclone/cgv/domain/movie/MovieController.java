package com.cgvclone.cgv.domain.movie;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import com.cgvclone.cgv.domain.movie.dto.MovieDetailResponse;
import com.cgvclone.cgv.domain.movie.dto.MovieListResponse;
import com.cgvclone.cgv.domain.movie_keeping.MovieKeepingService;
import com.cgvclone.cgv.domain.auth.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final MovieKeepingService movieKeepingService;

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

    @PostMapping("/{movieId}/keep")
    public ResponseEntity<Void> keepMovie(@PathVariable Long movieId,
            @AuthenticationPrincipal CustomUserDetails user) {
        movieKeepingService.keepMovie(user.getUserId(), movieId);
        return ResponseEntity
                .status(CREATED)
                .build();
    }

    @DeleteMapping("/{movieId}/keep")
    public ResponseEntity<Void> cancelMovieKeeping(@PathVariable Long movieId,
            @AuthenticationPrincipal CustomUserDetails user) {
        movieKeepingService.cancelMovieKeeping(user.getUserId(), movieId);
        return ResponseEntity
                .status(NO_CONTENT)
                .build();
    }
}
