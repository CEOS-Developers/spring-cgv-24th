package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.service.CinemaLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "영화관 찜", description = "영화관 찜 및 취소")
public class CinemaLikeController {
    private final CinemaLikeService cinemaLikeService;

    @PostMapping
    @Operation(summary = "영화관 찜")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "영화관 찜 성공")
    public ResponseEntity<Void> create(
            @PathVariable Long cinemaId,
            @RequestParam Long userId
    ) {
        cinemaLikeService.create(userId, cinemaId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    @Operation(summary = "영화관 찜 취소")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "영화관 찜 취소 성공")
    public ResponseEntity<Void> delete(
            @PathVariable Long cinemaId,
            @RequestParam Long userId
    ) {
        cinemaLikeService.delete(userId, cinemaId);
        return ResponseEntity.noContent().build();
    }
}
