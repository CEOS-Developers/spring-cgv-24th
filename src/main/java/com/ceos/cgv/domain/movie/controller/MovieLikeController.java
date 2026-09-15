package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.service.MovieLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies/{movieId}/likes")
@RequiredArgsConstructor
public class MovieLikeController {
    private final MovieLikeService movieLikeService;

    @PostMapping
    public ResponseEntity<Void> create(
            @PathVariable Long movieId,
            @RequestParam Long userId
    ) {
        movieLikeService.create(userId, movieId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @PathVariable Long movieId,
            @RequestParam Long userId
    ) {
        movieLikeService.delete(userId, movieId);
        return ResponseEntity.noContent().build();
    }
}
