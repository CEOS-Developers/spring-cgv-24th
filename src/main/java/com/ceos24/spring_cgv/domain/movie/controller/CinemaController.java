package com.ceos24.spring_cgv.domain.movie.controller;

import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaCreateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaUpdateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.response.CinemaResponse;
import com.ceos24.spring_cgv.domain.movie.exception.code.CinemaSuccessCode;
import com.ceos24.spring_cgv.domain.movie.service.CinemaService;
import com.ceos24.spring_cgv.global.apipayload.ApiResponse;
import com.ceos24.spring_cgv.global.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "영화관", description = "영화관 관련 API")
@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    @Operation(summary = "영화관 생성", description = "새로운 영화관을 등록합니다.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CinemaResponse> createCinema(
            @Valid @RequestBody CinemaCreateRequest request
    ) {

        return ApiResponse.onSuccess(CinemaSuccessCode.CINEMA_CREATED, cinemaService.createCinema(request));
    }

    @Operation(summary = "영화관 전체 조회", description = "등록된 모든 영화관을 조회합니다.")
    @GetMapping
    public ApiResponse<List<CinemaResponse>> findAllCinema() {

        return ApiResponse.onSuccess(CinemaSuccessCode.CINEMA_LIST_FETCHED, cinemaService.findAllCinema());
    }

    @Operation(summary = "영화관 단건 조회", description = "ID로 특정 영화관을 조회합니다.")
    @GetMapping("/{cinemaId}")
    public ApiResponse<CinemaResponse> findCinema(
            @PathVariable Long cinemaId
    ) {

        return ApiResponse.onSuccess(CinemaSuccessCode.CINEMA_FETCHED, cinemaService.findCinema(cinemaId));
    }

    @Operation(summary = "영화관 수정", description = "ID로 특정 영화관의 정보를 수정합니다.")
    @PutMapping("/{cinemaId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CinemaResponse> updateCinema(
            @PathVariable Long cinemaId,
            @Valid @RequestBody CinemaUpdateRequest request
    ) {

        return ApiResponse.onSuccess(CinemaSuccessCode.CINEMA_UPDATED, cinemaService.updateCinema(cinemaId, request));
    }

    @Operation(summary = "영화관 삭제", description = "ID로 특정 영화관을 삭제합니다.")
    @DeleteMapping("/{cinemaId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteCinema(
            @PathVariable Long cinemaId
    ) {

        cinemaService.deleteCinema(cinemaId);

        return ApiResponse.onSuccess(CinemaSuccessCode.CINEMA_DELETED);
    }
}