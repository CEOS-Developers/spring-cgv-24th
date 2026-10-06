package com.ceos24.cgv.domain.theater.dto.response;

import com.ceos24.cgv.domain.theater.dto.ScreenInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "상영관 목록 조회 응답 DTO")
public record GetScreenResponse(
        @Schema(description = "영화관 ID", example = "1") Long theaterId,
        @Schema(description = "상영관 목록") List<ScreenInfo> screens) {}
