package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.TheaterDetailResponse;
import com.ceos24.cgv.domain.theater.dto.response.TheaterListResponse;
import com.ceos24.cgv.domain.theater.service.TheaterService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
@Tag(
        name = "영화관 API"
)
public class TheaterController {

    private final TheaterService theaterService;

    // 1. 영화관 전체 조회
    @Operation(
            summary = "영화관 전체 조회",
            description = "전체 영화관 목록을 영화관 이름 오름차순으로 조회합니다."
    )
    @GetMapping
    public ApiResponse<List<TheaterListResponse>> getTheaters() {
        List<TheaterListResponse> response =
                theaterService.getTheaters();

        return ApiResponse.onSuccess(
                "영화관 목록 조회에 성공했습니다.",
                response
        );
    }

    // 2. 영화관 단건 조회
    @Operation(
            summary = "영화관 단건 조회",
            description = "영화관 ID를 이용해 영화관 정보와 해당 영화관의 상영관 목록을 조회합니다."
    )
    @GetMapping("/{theaterId}")
    public ApiResponse<TheaterDetailResponse> getTheater(
            @PathVariable Long theaterId
    ) {
        TheaterDetailResponse response =
                theaterService.getTheater(theaterId);

        return ApiResponse.onSuccess(
                "영화관 상세 조회에 성공했습니다.",
                response
        );
    }
}