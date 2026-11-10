package com.ceos24.cgv.domain.screening.dto.response;

import com.ceos24.cgv.domain.screening.dto.ScreeningSeatInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "좌석 목록 조회 응답 DTO")
public record GetScreeningSeatResponse(
        @Schema(description = "상영일정 ID", example = "1") Long screeningId,
        @Schema(description = "좌석 목록") List<ScreeningSeatInfo> seats) {}
