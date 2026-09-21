package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.request.TheaterCreateRequest;
import com.ceos24.cgv.domain.theater.dto.request.TheaterUpdateRequest;
import com.ceos24.cgv.domain.theater.dto.response.TheaterResponse;
import com.ceos24.cgv.domain.theater.service.TheaterService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Theater", description = "영화관 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
public class TheaterController {

    private final TheaterService theaterService;

    @Operation(summary = "영화관 생성 API", description = "새로운 영화관 데이터를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createTheater(@Valid @RequestBody TheaterCreateRequest request) {
        Long theaterId = theaterService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, theaterId));
    }

    @Operation(summary = "영화관 목록 조회 API", description = "전체 영화관 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<TheaterResponse>> getTheaters() {
        return ApiResponse.onSuccess(theaterService.findAll());
    }

    @Operation(summary = "영화관 상세 조회 API", description = "특정 영화관의 상세 정보를 조회합니다.")
    @GetMapping("/{theaterId}")
    public ApiResponse<TheaterResponse> getTheater(@PathVariable Long theaterId) {
        return ApiResponse.onSuccess(theaterService.findById(theaterId));
    }

    @Operation(summary = "영화관 수정 API", description = "특정 영화관 정보를 수정합니다.")
    @PatchMapping("/{theaterId}")
    public ApiResponse<Void> updateTheater(
            @PathVariable Long theaterId,
            @Valid @RequestBody TheaterUpdateRequest request
    ) {
        theaterService.update(theaterId, request);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "영화관 삭제 API", description = "특정 영화관을 삭제합니다.")
    @DeleteMapping("/{theaterId}")
    public ApiResponse<Void> deleteTheater(@PathVariable Long theaterId) {
        theaterService.delete(theaterId);
        return ApiResponse.onSuccess(null);
    }
}
