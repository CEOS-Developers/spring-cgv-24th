package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.dto.CinemaResponse;
import com.ceos.cgv.domain.cinema.service.CinemaService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaController {
    private final CinemaService cinemaService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaResponse>>> findAll(){
        return ResponseEntity.ok(ApiResponse.success(cinemaService.findAll(), CinemaResponse::from));
    }

    @GetMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<CinemaResponse>> findById(@PathVariable Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(CinemaResponse.from(cinemaService.findById(cinemaId))));
    }
}
