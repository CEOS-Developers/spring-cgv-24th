package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.service.UserTheaterService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
@Tag(
        name = "영화관 찜 API",
        description = "영화관 찜 등록 및 취소 API"
)
public class UserTheaterController {

    private final UserTheaterService userTheaterService;

    // 1. 영화관 찜 등록
    @PostMapping("/{theaterId}/like")
    @Operation(
            summary = "영화관 찜 등록",
            description = "사용자가 특정 영화관을 찜합니다."
    )
    public ApiResponse<Void> likeTheater(
            @Parameter(description = "영화관 ID")
            @PathVariable(name = "theaterId")
            Long theaterId,

            @Parameter(description = "사용자 ID")
            @RequestParam(name = "userId")
            Long userId
    ) {
        userTheaterService.likeTheater(
                userId,
                theaterId
        );

        return ApiResponse.onSuccess(
                "영화관 찜 등록에 성공했습니다.",
                null
        );
    }

    // 2. 영화관 찜 취소
    @DeleteMapping("/{theaterId}/like")
    @Operation(
            summary = "영화관 찜 취소",
            description = "사용자가 찜한 특정 영화관을 찜 목록에서 삭제합니다."
    )
    public ApiResponse<Void> unlikeTheater(
            @Parameter(description = "영화관 ID")
            @PathVariable(name = "theaterId")
            Long theaterId,

            @Parameter(description = "사용자 ID")
            @RequestParam(name = "userId")
            Long userId
    ) {
        userTheaterService.unlikeTheater(
                userId,
                theaterId
        );

        return ApiResponse.onSuccess(
                "영화관 찜 취소에 성공했습니다.",
                null
        );
    }
}