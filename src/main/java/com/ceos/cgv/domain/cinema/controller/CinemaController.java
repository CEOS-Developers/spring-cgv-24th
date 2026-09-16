package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.dto.CinemaResponse;
import com.ceos.cgv.domain.cinema.service.CinemaService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "영화관", description = "영화관 조회")
public class CinemaController {
    private final CinemaService cinemaService;

    @GetMapping
    @Operation(summary = "영화관 전체 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영화관 목록 조회 성공")
    public ResponseEntity<ApiResponse<List<CinemaResponse>>> findAll(){
        return ResponseEntity.ok(ApiResponse.success(cinemaService.findAll(), CinemaResponse::from));
    }

    @GetMapping("/{cinemaId}")
    @Operation(summary = "영화관 상세 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영화관 상세 조회 성공")
    public ResponseEntity<ApiResponse<CinemaResponse>> findById(@PathVariable Long cinemaId) {
        return ResponseEntity.ok(ApiResponse.success(CinemaResponse.from(cinemaService.findById(cinemaId))));
    }
}
