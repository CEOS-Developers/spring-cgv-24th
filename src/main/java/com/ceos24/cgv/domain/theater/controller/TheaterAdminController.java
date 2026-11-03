package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.request.ScreenCreateRequest;
import com.ceos24.cgv.domain.theater.dto.request.TheaterCreateRequest;
import com.ceos24.cgv.domain.theater.dto.request.TheaterUpdateRequest;
import com.ceos24.cgv.domain.theater.dto.response.ScreenCreateResponse;
import com.ceos24.cgv.domain.theater.service.ScreenService;
import com.ceos24.cgv.domain.theater.service.TheaterService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import com.ceos24.cgv.global.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Theater (관리자)", description = "영화관 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/theaters")
@SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
public class TheaterAdminController {

    private final TheaterService theaterService;
    private final ScreenService screenService;

    @Operation(summary = "영화관 생성 API", description = "관리자 권한으로 새로운 영화관 데이터를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createTheater(@Valid @RequestBody TheaterCreateRequest request) {
        Long theaterId = theaterService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, theaterId));
    }

    @Operation(summary = "영화관 수정 API", description = "특정 영화관 정보를 수정합니다.")
    @PatchMapping("/{theaterId}")
    public ApiResponse<Void> updateTheater(@PathVariable Long theaterId,
                                           @Valid @RequestBody TheaterUpdateRequest request) {
        theaterService.update(theaterId, request);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "영화관 삭제 API", description = "특정 영화관을 삭제합니다.")
    @DeleteMapping("/{theaterId}")
    public ApiResponse<Void> deleteTheater(@PathVariable Long theaterId) {
        theaterService.delete(theaterId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "상영관 등록 API", description = "영화관에 상영관을 등록하고 상영관 타입 규격대로 좌석을 생성합니다.")
    @PostMapping("/{theaterId}/screens")
    public ResponseEntity<ApiResponse<ScreenCreateResponse>> createScreen(
            @PathVariable Long theaterId,
            @Valid @RequestBody ScreenCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(SuccessStatus.CREATED, screenService.create(theaterId, request)));
    }
}
