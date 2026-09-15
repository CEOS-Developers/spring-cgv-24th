package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.service.CinemaLikeService;
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
@RequestMapping("/api/v1/cinemas/{cinemaId}/likes")
@RequiredArgsConstructor
public class CinemaLikeController {
    private final CinemaLikeService cinemaLikeService;

    @PostMapping
    public ResponseEntity<Void> create(
            @PathVariable Long cinemaId,
            @RequestParam Long userId
    ) {
        cinemaLikeService.create(userId, cinemaId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @PathVariable Long cinemaId,
            @RequestParam Long userId
    ) {
        cinemaLikeService.delete(userId, cinemaId);
        return ResponseEntity.noContent().build();
    }
}
