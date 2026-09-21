package com.ceos24.cgv.domain.cinema.controller;

import com.ceos24.cgv.domain.cinema.dto.request.CinemaCreateRequest;
import com.ceos24.cgv.domain.cinema.dto.response.CinemaResponse;
import com.ceos24.cgv.domain.cinema.service.CinemaService;
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
        name = "영화관",
        description = "영화관 등록, 목록 조회, 상세 조회 및 삭제 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cinemas")
public class CinemaController {

    private final CinemaService cinemaService;

    @Operation(
            summary = "영화관 등록",
            description = "영화관 이름, 주소, 지역 정보를 입력하여 영화관을 등록합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createCinema(
            @Valid @RequestBody CinemaCreateRequest request
    ) {
        Long cinemaId = cinemaService.createCinema(request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;
        ApiResponse<Long> body = new ApiResponse<>(cinemaId, code.getStatus(), code.getMessage());

        return ResponseEntity.created(URI.create("/api/cinemas/" + cinemaId)).body(body);
    }

    @Operation(
            summary = "영화관 목록 조회",
            description = "등록된 모든 영화관을 ID 오름차순으로 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaResponse>>> getCinemas() {
        List<CinemaResponse> responses = cinemaService.getCinemas();
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        ApiResponse<List<CinemaResponse>> body =
                new ApiResponse<>(responses, code.getStatus(), code.getMessage());

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @Operation(
            summary = "영화관 상세 조회",
            description = "영화관 ID를 이용하여 특정 영화관의 정보를 조회합니다."
    )
    @GetMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<CinemaResponse>> getCinema(
            @PathVariable("cinemaId") Long cinemaId
    ) {
        CinemaResponse response = cinemaService.getCinema(cinemaId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        ApiResponse<CinemaResponse> body =
                new ApiResponse<>(response, code.getStatus(), code.getMessage());

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @Operation(
            summary = "영화관 삭제",
            description = "영화관 ID에 해당하는 영화관을 삭제합니다."
    )
    @DeleteMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<Void>> deleteCinema(
            @PathVariable("cinemaId") Long cinemaId
    ) {
        cinemaService.deleteCinema(cinemaId);
        SuccessCode code = SuccessCode.DELETE_SUCCESS;
        ApiResponse<Void> body = new ApiResponse<>(null, code.getStatus(), code.getMessage());

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
