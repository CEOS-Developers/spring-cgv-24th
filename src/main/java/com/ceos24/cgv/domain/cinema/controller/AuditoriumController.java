package com.ceos24.cgv.domain.cinema.controller;

import com.ceos24.cgv.domain.cinema.dto.request.AuditoriumCreateRequest;
import com.ceos24.cgv.domain.cinema.dto.response.AuditoriumResponse;
import com.ceos24.cgv.domain.cinema.service.AuditoriumService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(
        name = "상영관",
        description = "영화관 내부의 상영관 등록 및 목록 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cinemas/{cinemaId}/auditoriums")
public class AuditoriumController {

    private final AuditoriumService auditoriumService;

    @Operation(
            summary = "상영관 등록",
            description = "영화관에 상영관을 등록하고 상영관 종류의 행·열 크기에 맞춰 좌석을 함께 생성합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createAuditorium(
            @PathVariable("cinemaId") Long cinemaId,
            @Valid @RequestBody AuditoriumCreateRequest request
    ) {
        Long auditoriumId = auditoriumService.createAuditorium(cinemaId, request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;
        ApiResponse<Long> body = new ApiResponse<>(
                auditoriumId,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.created(URI.create(
                "/api/cinemas/" + cinemaId + "/auditoriums/" + auditoriumId
        )).body(body);
    }

    @Operation(
            summary = "상영관 목록 조회",
            description = "특정 영화관에 속한 모든 상영관을 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditoriumResponse>>> getAuditoriums(
            @PathVariable("cinemaId") Long cinemaId
    ) {
        List<AuditoriumResponse> auditoriums = auditoriumService.getAuditoriums(cinemaId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        ApiResponse<List<AuditoriumResponse>> body = new ApiResponse<>(
                auditoriums,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
